package com.twobgreen.gridintelligence.infrastructure.odre;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OdreNumbersTests {

	@Test
	void numberSentAsTextOrAsNumberGivesTheSameValue() {
		// hydraulique_fil_eau_eclusee is declared as text in tr: "2458"; NationalRealtimeRecord keeps 2458 as "2458"
		assertThat(OdreNumbers.wholeNumber("f", "2458")).isEqualTo(2458);
		assertThat(OdreNumbers.wholeNumber("f", " 2458 ")).isEqualTo(2458);
	}

	@Test
	void zeroAndNegativeValuesAreKept() {
		assertThat(OdreNumbers.wholeNumber("f", "0")).isZero();
		assertThat(OdreNumbers.wholeNumber("f", "-2074")).isEqualTo(-2074);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", " ", "ND", "-", "abc", "1 234", "1,5", "NaN" })
	void anythingThatIsNotACleanNumberIsMissing(String raw) {
		assertThat(OdreNumbers.wholeNumber("f", raw)).isNull();
		assertThat(OdreNumbers.decimal("f", raw)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234.5", "49.97", "99999999999" })
	void wholeNumberRefusesDecimalsAndOutOfRangeValuesInsteadOfRounding(String raw) {
		assertThat(OdreNumbers.wholeNumber("f", raw)).isNull();
	}

	@Test
	void wholeNumberAcceptsADecimalWithNoFraction() {
		assertThat(OdreNumbers.wholeNumber("f", "1234.0")).isEqualTo(1234);
	}

	@Test
	void decimalKeepsTheFraction() {
		assertThat(OdreNumbers.decimal("f", "29.5")).isEqualByComparingTo(new BigDecimal("29.5"));
	}
}
