package com.studentleague.users.repository;

import com.studentleague.users.domain.Role;
import com.studentleague.users.domain.RoleStatus;
import com.studentleague.users.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSub(String googleSub);

    @Query("""
            select u from User u
            where lower(u.email) like lower(concat('%', :q, '%'))
               or lower(coalesce(u.firstName, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(u.lastName, '')) like lower(concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    @Query("""
            select u from User u
            where u.role = :role
               or exists (
                    select a.id from UserRoleAssignment a
                    where a.userId = u.id
                      and a.role = :role
                      and a.status = :status
               )
            """)
    Page<User> findReferees(
            @Param("role") Role role,
            @Param("status") RoleStatus status,
            Pageable pageable
    );

    @Query("""
            select u from User u
            where (
                u.role = :role
                or exists (
                    select a.id from UserRoleAssignment a
                    where a.userId = u.id
                      and a.role = :role
                      and a.status = :status
                )
            )
            and (
                lower(u.email) like lower(concat('%', :q, '%'))
                or lower(coalesce(u.firstName, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(u.lastName, '')) like lower(concat('%', :q, '%'))
            )
            """)
    Page<User> searchReferees(
            @Param("q") String q,
            @Param("role") Role role,
            @Param("status") RoleStatus status,
            Pageable pageable
    );
}
