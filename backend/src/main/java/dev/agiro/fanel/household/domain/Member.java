package dev.agiro.fanel.household.domain;

import dev.agiro.fanel.household.api.MemberRole;
import dev.agiro.fanel.shared.domain.UuidEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "member")
public class Member extends UuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;
    private String name;
    @Enumerated(EnumType.STRING)
    private MemberRole role;
    private String color;
    private String pin;
    private String username;
    private String passwordHash;
    private String oidcSubject;
    private Instant createdAt;

    /** Adults (or admins) responsible for this member, when it's a child. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "member_guardian",
            joinColumns = @JoinColumn(name = "child_id"),
            inverseJoinColumns = @JoinColumn(name = "guardian_id"))
    private Set<Member> guardians = new HashSet<>();

    /** Children this member (an adult/admin) is responsible for. Inverse side of {@link #guardians}. */
    @ManyToMany(mappedBy = "guardians", fetch = FetchType.LAZY)
    private Set<Member> wards = new HashSet<>();

    protected Member() {}

    public Member(Household household, String name, MemberRole role, String color) {
        this.household = household;
        this.name = name;
        this.role = role;
        this.color = color;
        this.createdAt = Instant.now();
    }

    public Household getHousehold() { return household; }
    public String getName() { return name; }
    public MemberRole getRole() { return role; }
    public String getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }
    public void setRole(MemberRole role) { this.role = role; }
    public void setName(String name) { this.name = name; }
    public void setColor(String color) { this.color = color; }

    public boolean hasPin() { return pin != null && !pin.isBlank(); }
    public boolean matchesPin(String candidate) { return !hasPin() || pin.equals(candidate); }
    public void setPin(String pin) { this.pin = pin; }

    public String getUsername() { return username; }
    public boolean hasCredentials() { return username != null && passwordHash != null; }
    public String getPasswordHash() { return passwordHash; }

    public void setCredentials(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String getOidcSubject() { return oidcSubject; }
    public void setOidcSubject(String oidcSubject) { this.oidcSubject = oidcSubject; }

    public List<UUID> guardianIds() { return guardians.stream().map(UuidEntity::getId).toList(); }
    public List<UUID> wardIds() { return wards.stream().map(UuidEntity::getId).toList(); }
    public void setGuardians(Set<Member> guardians) { this.guardians = guardians; }
}
