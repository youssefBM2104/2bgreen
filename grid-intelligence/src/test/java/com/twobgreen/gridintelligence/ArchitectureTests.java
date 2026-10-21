package com.twobgreen.gridintelligence;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.twobgreen.gridintelligence")
class ArchitectureTests {

	@ArchTest
	static final ArchRule domainStaysPure = noClasses()
		.that().resideInAPackage("..domain..")
		.should().dependOnClassesThat()
		.resideInAnyPackage("..application..", "..infrastructure..", "org.springframework..");

	@ArchTest
	static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
		.that().resideInAPackage("..application..")
		.should().dependOnClassesThat()
		.resideInAPackage("..infrastructure..");
}
