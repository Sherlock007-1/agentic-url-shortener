package com.agenticsdlc.shortener.url.repository;

import com.agenticsdlc.shortener.url.domain.ClickEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

	long countByShortUrlId(Long shortUrlId);

	/** Most recent clicks first; the caller bounds the size with a {@link Pageable}. */
	List<ClickEvent> findByShortUrlIdOrderByClickedAtDesc(Long shortUrlId, Pageable pageable);
}
