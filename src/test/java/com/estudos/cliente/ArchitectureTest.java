package com.estudos.cliente;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regra de dependência da arquitetura hexagonal: {@code api → application → domain ← infrastructure}.
 */
class ArchitectureTest {

    private static final String BASE = "com.estudos.cliente";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void domainDependsOnlyOnTheJdk() {
        classes().that().resideInAPackage(BASE + ".domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(BASE + ".domain..", "java..")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotKnowFrameworksOrAdapters() {
        classes().that().resideInAPackage(BASE + ".application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(BASE + ".application..", BASE + ".domain..", "java..", "org.slf4j..")
                .check(CLASSES);
    }

    @Test
    void adaptersDoNotDependOnEachOther() {
        noClasses().that().resideInAPackage(BASE + ".infrastructure..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".api..")
                .check(CLASSES);
        noClasses().that().resideInAPackage(BASE + ".api..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure..")
                .check(CLASSES);
    }
}
