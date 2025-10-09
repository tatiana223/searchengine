package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    Optional<IndexEntity> findByPageAndLemma(PageEntity page, LemmaEntity lemma);
    List<IndexEntity> findAllByPage(PageEntity page);
    List<PageEntity> findPagesByLemma(LemmaEntity page);
    List<IndexEntity> findByLemma(LemmaEntity lemma);
    List<IndexEntity> findByPageInAndLemmaIn(List<PageEntity> pages, List<LemmaEntity> lemmas);
    @Modifying
    @Query(value = "DELETE i FROM index_page i INNER JOIN lemma l ON i.lemma_id = l.id WHERE l.site_id = :siteId", nativeQuery = true)
    void deleteBySite(@Param("siteId") Integer siteId);
}
