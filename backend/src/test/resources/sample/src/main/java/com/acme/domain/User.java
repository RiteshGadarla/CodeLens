package com.acme.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class User {

    @Id
    private Long id;

    private String name;

    protected User() {
    }

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
