package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    Optional<Page> findByPathAndSite(String path, Site site);

    boolean existsByPathAndSite(String path, Site site);

    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site = :site")
    void deleteBySite(@Param("site") Site site);

    @Query("SELECT COUNT(p) FROM Page p WHERE p.site = :site")
    long countBySite(@Param("site") Site site);
}