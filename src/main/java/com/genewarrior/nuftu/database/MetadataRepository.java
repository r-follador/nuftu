package com.genewarrior.nuftu.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MetadataRepository extends JpaRepository<Metadata, Long>, JpaSpecificationExecutor<Metadata> {
    Optional<Metadata> findByCreator(String creator);

    Optional<Metadata> findByTokennumber(Long tokennumber);

    Optional<Metadata> findByUuid(String uuid);

    List<Metadata> findByTokenState(Metadata.TokenState tokenState);

    @Transactional
    @Modifying
    @Query("update metadata m set m.tokenState = ?1 where m.uuid= ?2")
    void setTokenstateByUuid(Metadata.TokenState tokenState, String uuid);

    @Transactional
    @Modifying
    @Query("update metadata m set m.tokenState = ?1, m.tokennumber = ?2 where m.uuid= ?3")
    void setTokenstateAndTokennumberByUuid(Metadata.TokenState tokenState, long tokennumber, String uuid);

}