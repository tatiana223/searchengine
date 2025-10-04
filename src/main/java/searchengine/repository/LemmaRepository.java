package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Optional<LemmaEntity> findByLemmaAndSite(String lemma, SiteEntity site);
}
