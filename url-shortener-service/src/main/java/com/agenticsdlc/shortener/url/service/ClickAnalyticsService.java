package com.agenticsdlc.shortener.url.service;

import com.agenticsdlc.shortener.url.api.dto.ShortUrlAnalyticsResponse;
import com.agenticsdlc.shortener.url.api.dto.ShortUrlAnalyticsResponse.ClickResponse;
import com.agenticsdlc.shortener.url.domain.ClickEvent;
import com.agenticsdlc.shortener.url.domain.ShortUrl;
import com.agenticsdlc.shortener.url.repository.ClickEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Click analytics: one persisted event per successful redirect.
 *
 * <p>Only {@link ShortUrlService} decides <em>whether</em> a redirect succeeded;
 * this service only records and reads. Nothing is recorded for unknown, disabled
 * or expired short codes.
 */
@Service
public class ClickAnalyticsService {

	/** Size of the "recent clicks" window returned by the analytics endpoint. */
	static final int RECENT_CLICK_WINDOW = 10;

	private final ClickEventRepository clickEventRepository;

	public ClickAnalyticsService(ClickEventRepository clickEventRepository) {
		this.clickEventRepository = clickEventRepository;
	}

	/** Records exactly one click for a short URL that has just resolved successfully. */
	@Transactional
	public void recordClick(ShortUrl shortUrl, Instant clickedAt, String referrer) {
		clickEventRepository.save(new ClickEvent(shortUrl.getId(), clickedAt, referrer));
	}

	@Transactional(readOnly = true)
	public ShortUrlAnalyticsResponse analytics(ShortUrl shortUrl) {
		long total = clickEventRepository.countByShortUrlId(shortUrl.getId());
		List<ClickEvent> recent = clickEventRepository.findByShortUrlIdOrderByClickedAtDesc(shortUrl.getId(),
				PageRequest.of(0, RECENT_CLICK_WINDOW));
		Instant lastClickedAt = recent.isEmpty() ? null : recent.get(0).getClickedAt();
		return new ShortUrlAnalyticsResponse(shortUrl.getShortCode(), total, lastClickedAt,
				recent.stream().map(click -> new ClickResponse(click.getClickedAt(), click.getReferrer())).toList());
	}
}
