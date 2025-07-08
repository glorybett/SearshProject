package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Query("SELECT s FROM Site s WHERE s.url = :url ORDER BY s.id LIMIT 1")
    Optional<Site> findFirstByUrl(@Param("url") String url);

    @Query("SELECT s FROM Site s WHERE s.url = :url")
    Optional<Site> findByUrl(@Param("url") String url);

    List<Site> findByStatus(Site.Status status);

    Optional<Site> findById(Integer id);
}