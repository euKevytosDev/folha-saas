package com.sacolao.user.repository;

import com.sacolao.user.entity.User;
import com.sacolao.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    List<User> findByEstablishment_IdOrderByCreatedAtAsc(UUID establishmentId);

    Optional<User> findByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    @Query("select u from User u left join fetch u.establishment where u.id = :id")
    Optional<User> findWithEstablishmentById(@Param("id") UUID id);

    @Query("select u from User u left join fetch u.establishment where u.email = :email")
    Optional<User> findWithEstablishmentByEmail(@Param("email") String email);
}
