package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {
    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site = :site")
    Optional<Lemma> findByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma")
    List<Lemma> findByLemma(@Param("lemma") String lemma);

    @Query("SELECT l FROM Lemma l WHERE l.lemma IN :lemmas AND l.site = :site")
    List<Lemma> findByLemmaInAndSite(@Param("lemmas") List<String> lemmas, @Param("site") Site site);

    @Modifying
    @Transactional
    @Query("UPDATE Lemma l SET l.frequency = l.frequency - 1 WHERE l IN " +
            "(SELECT i.lemma FROM Index i WHERE i.page = :page)")
    void decrementFrequencyForPage(@Param("page") searchengine.model.Page page);

    @Query("SELECT COUNT(l) FROM Lemma l WHERE l.site = :site")
    int countBySite(@Param("site") Site site);

    @Modifying
    @Transactional
    @Query("DELETE FROM Lemma l WHERE l.site = :site")
    void deleteBySite(@Param("site") Site site);
}