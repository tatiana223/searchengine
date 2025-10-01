package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    // Удаляем все индексы для определенной страницы
    @Modifying
    @Query("DELETE FROM IndexEntity i WHERE i.page = :page")
    void deleteByPage(@Param("page") PageEntity page);

    // Находим все индексы для страницы
    List<IndexEntity> findByPage(PageEntity page);
}