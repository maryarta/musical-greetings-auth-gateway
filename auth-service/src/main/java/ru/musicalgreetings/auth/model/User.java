package ru.musicalgreetings.auth.model;

import jakarta.persistence.*;
import ru.musicalgreetings.auth.data.AccountType;

import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    public User(){

    }
    public User(AccountType accountType) {
        this.accountType = accountType;
    }

    public User(UUID userId, AccountType accountType) {
        this.id = userId;
        this.accountType = accountType;
    }

    public UUID getId() {
        return id;
    }

    public AccountType getAccountType() {
        return accountType;
    }

}
