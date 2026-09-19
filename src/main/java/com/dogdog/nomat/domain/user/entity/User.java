package com.dogdog.nomat.domain.user.entity;

import com.dogdog.nomat.domain.asset.entity.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", length = 50, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "nickname", length = 50, nullable = false, unique = true)
    private String nickname;

    @Column(name = "email", length = 254, unique = true)
    private String email;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_asset_id")
    private Asset profileImageAsset;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(String loginId, String passwordHash, String nickname, String email) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.email = email;
    }

    public static User create(String loginId, String passwordHash, String nickname) {
        return create(loginId, passwordHash, nickname, null);
    }

    public static User create(String loginId, String passwordHash, String nickname, String email) {
        return new User(loginId, passwordHash, nickname, email);
    }

    public void changeProfile(String nickname, Asset profileImageAsset, String email) {
        this.nickname = nickname;
        this.profileImageAsset = profileImageAsset;
        if (!java.util.Objects.equals(this.email, email)) {
            this.email = email;
            this.emailVerifiedAt = null;
        }
    }

    public void verifyEmail(String email, LocalDateTime verifiedAt) {
        this.email = email;
        this.emailVerifiedAt = verifiedAt;
    }

    public boolean hasVerifiedEmail() {
        return email != null && emailVerifiedAt != null;
    }

    public void removeProfileImage() {
        this.profileImageAsset = null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void delete() {
        if (status == UserStatus.DELETED) {
            return;
        }

        this.loginId = "deleted_user_" + id;
        this.passwordHash = "deleted";
        this.nickname = "deleted_user_" + id;
        this.email = null;
        this.emailVerifiedAt = null;
        this.profileImageAsset = null;
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
