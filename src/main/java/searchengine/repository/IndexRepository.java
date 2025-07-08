package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {
    @Query("SELECT i.page FROM Index i WHERE i.lemma = :lemma")
    List<Page> findPagesByLemma(@Param("lemma") Lemma lemma);

    @Query("SELECT i.page FROM Index i WHERE i.lemma = :lemma AND i.page IN :pages")
    List<Page> findPagesByLemmaAndPageIn(
            @Param("lemma") Lemma lemma,
            @Param("pages") List<Page> pages
    );

    @Query("SELECT SUM(i.rank) FROM Index i WHERE i.page = :page AND i.lemma.lemma IN :lemmas")
    Double sumRankByPageAndLemmas(
            @Param("page") Page page,
            @Param("lemmas") List<String> lemmas
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM Index i WHERE i.page = :page")
    void deleteByPage(@Param("page") Page page);

    @Modifying
    @Transactional
    @Query("DELETE FROM Index i WHERE i.page IN (SELECT p FROM Page p WHERE p.site = :site)")
    void deleteBySite(@Param("site") Site site);
}