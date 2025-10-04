package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.model.LemmaEntity;

import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    Optional<IndexEntity> findByPageAndLemma(PageEntity page, LemmaEntity lemma);
    List<IndexEntity> findAllByPage(PageEntity page);
}
