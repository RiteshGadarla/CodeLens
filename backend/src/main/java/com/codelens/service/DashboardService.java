package com.codelens.service;

import com.codelens.api.dto.DashboardDto;
import com.codelens.api.dto.DashboardDto.DayActivity;
import com.codelens.api.dto.DashboardDto.PortfolioHotspot;
import com.codelens.api.dto.DashboardDto.ProjectKpi;
import com.codelens.domain.*;
import com.codelens.graph.GraphNode;
import com.codelens.repository.ProjectRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// sql aggregates, so the dashboard never loads project graphs
@Service
public class DashboardService {

    public static final int ACTIVITY_DAYS = 14;
    private static final int HOTSPOTS = 8;
    private static final String TYPE_KINDS = kinds(EntityKind::isType);
    private static final String SCORED_KINDS =
            kinds(k -> k.isType() || k == EntityKind.METHOD || k == EntityKind.CONSTRUCTOR);

    private record Files(long files, long loc) {
    }

    private record Entities(long types, long methods, long endpoints, long tests) {
    }

    private record Risk(long high, long medium, long low, double avg, double max) {
    }

    private record LastRun(RunStatus status, Instant at, Long durationMs) {
    }

    private final JdbcTemplate jdbc;
    private final ProjectRepository projects;
    private final AnalysisService analysis;

    public DashboardService(JdbcTemplate jdbc, ProjectRepository projects, AnalysisService analysis) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.analysis = analysis;
    }

    public DashboardDto forOwner(long ownerId) {
        List<Project> owned = projects.findByOwnerIdOrderByCreatedAtDesc(ownerId);

        Map<Long, Files> files = byProject("""
                select f.project_id, count(*) as files, coalesce(sum(f.loc), 0) as loc
                from source_file f join project p on p.id = f.project_id
                where p.owner_id = ? group by f.project_id""",
                (rs, i) -> new Files(rs.getLong("files"), rs.getLong("loc")), ownerId);

        Map<Long, Entities> entities = byProject("""
                select e.project_id,
                       count(*) filter (where e.kind in (%1$s)) as types,
                       count(*) filter (where e.kind in ('METHOD', 'CONSTRUCTOR')) as methods,
                       count(*) filter (where e.kind = 'ENDPOINT') as endpoints,
                       count(*) filter (where e.kind in (%1$s) and e.stereotype = 'TEST') as tests
                from code_entity e join project p on p.id = e.project_id
                where p.owner_id = ? group by e.project_id""".formatted(TYPE_KINDS),
                (rs, i) -> new Entities(rs.getLong("types"), rs.getLong("methods"), rs.getLong("endpoints"),
                        rs.getLong("tests")), ownerId);

        Map<Long, Long> edges = byProject("""
                select d.project_id, count(*) as edges
                from dependency_edge d join project p on p.id = d.project_id
                where p.owner_id = ? group by d.project_id""",
                (rs, i) -> rs.getLong("edges"), ownerId);

        // thresholds mirror RiskScorer.level
        Map<Long, Risk> risk = byProject("""
                select m.project_id,
                       count(*) filter (where m.risk_score >= 60) as high,
                       count(*) filter (where m.risk_score >= 30 and m.risk_score < 60) as medium,
                       count(*) filter (where m.risk_score < 30) as low,
                       avg(m.risk_score) as avg, max(m.risk_score) as max
                from entity_metric m
                join code_entity e on e.id = m.entity_id
                join project p on p.id = m.project_id
                where p.owner_id = ? and e.kind in (%s) group by m.project_id""".formatted(SCORED_KINDS),
                (rs, i) -> new Risk(rs.getLong("high"), rs.getLong("medium"), rs.getLong("low"), rs.getDouble("avg"),
                        rs.getDouble("max")), ownerId);

        Map<Long, LastRun> lastRuns = byProject("""
                select distinct on (r.project_id) r.project_id, r.status, coalesce(r.finished_at, r.started_at) as at,
                       r.duration_ms
                from analysis_run r join project p on p.id = r.project_id
                where p.owner_id = ? order by r.project_id, r.started_at desc""",
                (rs, i) -> new LastRun(RunStatus.valueOf(rs.getString("status")), rs.getTimestamp("at").toInstant(),
                        (Long) rs.getObject("duration_ms")), ownerId);

        var rows = owned.stream().map(p -> {
            long id = p.getId();
            Files f = files.getOrDefault(id, new Files(0, 0));
            Entities e = entities.getOrDefault(id, new Entities(0, 0, 0, 0));
            Risk r = risk.getOrDefault(id, new Risk(0, 0, 0, 0, 0));
            LastRun run = lastRuns.get(id);
            return new ProjectKpi(id, p.getName(), p.getSourceType(), p.getSourceUri(), p.getStatus(),
                    analysis.isRunning(id), f.files(), f.loc(), e.types(), e.methods(), e.endpoints(), e.tests(),
                    edges.getOrDefault(id, 0L), r.high(), r.medium(), r.low(), r.avg(), r.max(),
                    run == null ? null : run.status(), run == null ? null : run.at(),
                    run == null ? null : run.durationMs());
        }).toList();

        List<DayActivity> activity = activity(ownerId);
        return new DashboardDto(totals(rows, activity), rows, activity, hotspots(ownerId));
    }

    private List<DayActivity> activity(long ownerId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(ACTIVITY_DAYS - 1);
        Map<LocalDate, DayActivity> byDay = new HashMap<>();
        jdbc.query("""
                select (r.started_at at time zone 'UTC')::date as day,
                       count(*) filter (where r.status = 'SUCCESS') as ok,
                       count(*) filter (where r.status = 'FAILED') as failed,
                       avg(r.duration_ms) as avg_ms
                from analysis_run r join project p on p.id = r.project_id
                where p.owner_id = ? and r.started_at >= ?
                group by 1""", (RowCallbackHandler) rs -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            Object avg = rs.getObject("avg_ms");
            byDay.put(day, new DayActivity(day, rs.getInt("ok"), rs.getInt("failed"),
                    avg == null ? null : Math.round(((Number) avg).doubleValue())));
        }, ownerId, Timestamp.from(first.atStartOfDay().toInstant(ZoneOffset.UTC)));

        return first.datesUntil(today.plusDays(1))
                .map(d -> byDay.getOrDefault(d, new DayActivity(d, 0, 0, null)))
                .toList();
    }

    private List<PortfolioHotspot> hotspots(long ownerId) {
        return jdbc.query("""
                select p.id as project_id, p.name as project_name, e.id, e.qualified_name, e.name, e.kind,
                       e.stereotype, m.risk_score, m.dependents
                from entity_metric m
                join code_entity e on e.id = m.entity_id
                join project p on p.id = m.project_id
                where p.owner_id = ? and e.kind in (%s)
                order by m.risk_score desc, e.id
                limit %d""".formatted(TYPE_KINDS, HOTSPOTS), (rs, i) -> {
            EntityKind kind = EntityKind.valueOf(rs.getString("kind"));
            String role = rs.getString("stereotype");
            return new PortfolioHotspot(rs.getLong("project_id"), rs.getString("project_name"), rs.getLong("id"),
                    GraphNode.labelOf(rs.getString("qualified_name"), kind, rs.getString("name")), kind,
                    role == null ? null : Stereotype.valueOf(role), rs.getDouble("risk_score"), rs.getInt("dependents"));
        }, ownerId);
    }

    private static DashboardDto.Totals totals(List<ProjectKpi> rows, List<DayActivity> activity) {
        int analyzing = (int) rows.stream().filter(ProjectKpi::analyzing).count();
        int ready = (int) rows.stream().filter(r -> !r.analyzing() && r.status() == ProjectStatus.READY).count();
        int failed = (int) rows.stream().filter(r -> !r.analyzing() && r.status() == ProjectStatus.FAILED).count();
        int runs = activity.stream().mapToInt(d -> d.success() + d.failed()).sum();
        int failedRuns = activity.stream().mapToInt(DayActivity::failed).sum();
        // weighted by finished runs per day
        long weight = 0, total = 0;
        for (DayActivity d : activity) {
            if (d.avgMs() == null) continue;
            int n = d.success() + d.failed();
            weight += n;
            total += d.avgMs() * n;
        }
        return new DashboardDto.Totals(rows.size(), ready, analyzing, failed,
                sum(rows, ProjectKpi::files), sum(rows, ProjectKpi::loc), sum(rows, ProjectKpi::types),
                sum(rows, ProjectKpi::methods), sum(rows, ProjectKpi::endpoints), sum(rows, ProjectKpi::tests),
                sum(rows, ProjectKpi::edges), sum(rows, ProjectKpi::high), sum(rows, ProjectKpi::medium),
                sum(rows, ProjectKpi::low), runs, failedRuns, weight == 0 ? null : total / weight);
    }

    private <T> Map<Long, T> byProject(String sql, RowMapper<T> mapper, long ownerId) {
        Map<Long, T> out = new HashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs -> {
            out.put(rs.getLong("project_id"), mapper.mapRow(rs, 0));
        }, ownerId);
        return out;
    }

    private static long sum(List<ProjectKpi> rows, java.util.function.ToLongFunction<ProjectKpi> f) {
        return rows.stream().mapToLong(f).sum();
    }

    // enum names are constants, safe to inline
    private static String kinds(Predicate<EntityKind> filter) {
        return Arrays.stream(EntityKind.values()).filter(filter)
                .map(k -> "'" + k.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
