package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    Optional<PageEntity> findBySiteAndPath(SiteEntity site, String path);
    boolean existsBySiteAndPath(SiteEntity site, String path);
    int countBySite(SiteEntity site);
    @Modifying
    @Query("DELETE FROM PageEntity p WHERE p.site.id = :siteId")
    void deleteBySite(@Param("siteId") Integer siteId);
}
