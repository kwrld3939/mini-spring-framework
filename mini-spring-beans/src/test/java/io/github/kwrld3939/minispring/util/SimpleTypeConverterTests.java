package io.github.kwrld3939.minispring.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** 配置值与请求参数的类型转换：支持范围必须写清楚，不支持的类型必须报出类型名。 */
class SimpleTypeConverterTests {

	@Test
	void convertsStringAndNumberTypes() {
		assertEquals("text", SimpleTypeConverter.convert("text", String.class));
		assertEquals(Integer.valueOf(42), SimpleTypeConverter.convert("42", int.class));
		assertEquals(Integer.valueOf(42), SimpleTypeConverter.convert("42", Integer.class));
		assertEquals(Long.valueOf(42L), SimpleTypeConverter.convert("42", long.class));
		assertEquals(Long.valueOf(42L), SimpleTypeConverter.convert("42", Long.class));
		assertEquals(Double.valueOf(1.5d), SimpleTypeConverter.convert("1.5", double.class));
		assertEquals(Double.valueOf(1.5d), SimpleTypeConverter.convert("1.5", Double.class));
	}

	@Test
	void convertsBooleanWithParseBooleanSemantics() {
		assertEquals(Boolean.TRUE, SimpleTypeConverter.convert("true", boolean.class));
		assertEquals(Boolean.TRUE, SimpleTypeConverter.convert("TRUE", Boolean.class));
		assertEquals(Boolean.FALSE, SimpleTypeConverter.convert("anything-else", boolean.class),
				"Boolean.parseBoolean 对非 \"true\" 一律返回 false——这是已知语义，不是缺陷");
	}

	@Test
	void reportsUnsupportedTargetTypeWithItsName() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SimpleTypeConverter.convert("1", BigDecimal.class));

		assertTrue(ex.getMessage().contains("Unsupported target type"), ex.getMessage());
		assertTrue(ex.getMessage().contains("BigDecimal"), ex.getMessage());
	}

	@Test
	void propagatesNumberFormatFailure() {
		assertThrows(NumberFormatException.class, () -> SimpleTypeConverter.convert("abc", int.class));
	}
}
