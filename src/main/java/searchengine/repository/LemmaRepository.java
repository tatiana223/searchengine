package searchengine.repository;

import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.indexing.LemmaService;

import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Optional<LemmaEntity> findBySiteAndLemma(SiteEntity site, String lemma);

    @Modifying
    @Query("UPDATE LemmaEntity l SET l.frequency = l.frequency - 1" +
            "WHERE l IN (SELECT i.lemma FROM IndexEntity i WHERE i.page = :page)")
    void decrementFrequencyForPage(PageEntity page);

    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.frequency <= 0")
    void deleteZeroFrequencyLemmas();

    default void decrementOrDeleteLemmas(PageEntity page) {
        decrementFrequencyForPage(page);  // Уменьшаем frequency
        deleteZeroFrequencyLemmas();      // Удаляем нулевые леммы
    }

}
