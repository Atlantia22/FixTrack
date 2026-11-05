package com.fixtrack.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SortWhitelistTest {

	private static final Map<String, String> ALLOWED = Map.of("fullName", "fullName", "id", "id");

	@Test
	void shouldRejectSortingByAnInternalColumn() {
		Pageable byPassword = PageRequest.of(0, 20, Sort.by("password"));

		assertThatThrownBy(() -> SortWhitelist.restrict(byPassword, ALLOWED))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("No se puede ordenar por password");
	}

	@Test
	void shouldKeepAllowedFieldsAndAddTheIdAsTieBreaker() {
		Pageable restricted = SortWhitelist.restrict(PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "fullName")), ALLOWED);

		assertThat(restricted.getPageNumber()).isEqualTo(2);
		assertThat(restricted.getSort()).containsExactly(Sort.Order.desc("fullName"), Sort.Order.asc("id"));
	}

	@Test
	void shouldAlsoRestrictUnpagedListings() {
		Pageable restricted = SortWhitelist.restrict(Pageable.unpaged(Sort.by("fullName")), ALLOWED);

		assertThat(restricted.isUnpaged()).isTrue();
		assertThat(restricted.getSort()).containsExactly(Sort.Order.asc("fullName"), Sort.Order.asc("id"));
	}
}
