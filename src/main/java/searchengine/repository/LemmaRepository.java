package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Optional<LemmaEntity> findByLemmaAndSite(String lemmaSite, SiteEntity site );
    int countBySite(SiteEntity site);
    List<LemmaEntity> findByLemmaIn(Collection<String> lemmas);
    List<LemmaEntity> findByLemmaInAndSiteIn(List<String> lemmas, List<SiteEntity> sites);
    @Modifying
    @Query("DELETE FROM LemmaEntity l WHERE l.site.id = :siteId")
    void deleteBySite(@Param("siteId") Integer siteId);

}
