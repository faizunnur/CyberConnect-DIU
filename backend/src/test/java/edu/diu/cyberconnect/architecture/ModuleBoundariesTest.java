package edu.diu.cyberconnect.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Modular-monolith guardrails (ADR-0008). A module exposes only its {@code api} and {@code application} packages;
 * {@code domain} and {@code infrastructure} (entities, repositories, adapters) are private to the module.
 */
class ModuleBoundariesTest {

    private static final String ROOT = "edu.diu.cyberconnect";

    static final List<String> MODULES = List.of(
            "identity", "affiliation", "verification", "profile", "networking", "community", "opportunity",
            "research", "messaging", "notification", "impact", "moderation", "audit", "admin");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT);

    @Test
    void domainAndInfrastructureArePrivateToTheirModule() {
        for (String module : MODULES) {
            classes().that().resideInAnyPackage(pkg(module, "domain"), pkg(module, "infrastructure"))
                    .should().onlyHaveDependentClassesThat().resideInAPackage(ROOT + "." + module + "..")
                    .allowEmptyShould(true)
                    .because("other modules must use " + module + "'s application interfaces, DTOs or events")
                    .check(CLASSES);
        }
    }

    @Test
    void apiLayerIsNeverUsedByOtherCode() {
        for (String module : MODULES) {
            classes().that().resideInAPackage(pkg(module, "api"))
                    .should().onlyHaveDependentClassesThat().resideInAPackage(pkg(module, "api"))
                    .allowEmptyShould(true)
                    .check(CLASSES);
        }
    }

    @Test
    void apiLayerDoesNotTouchInfrastructureDirectly() {
        noClasses().that().resideInAPackage(ROOT + ".*.api..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".*.infrastructure..")
                .allowEmptyShould(true)
                .because("controllers delegate to application services")
                .check(CLASSES);
    }

    @Test
    void commonDoesNotDependOnFeatureModules() {
        String[] modulePackages = MODULES.stream().map(m -> ROOT + "." + m + "..").toArray(String[]::new);
        noClasses().that().resideInAPackage(ROOT + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(modulePackages)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void entitiesAreNeverReturnedByControllers() {
        noClasses().that().resideInAPackage(ROOT + ".*.api..")
                .should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class)
                .allowEmptyShould(true)
                .because("REST contracts use DTOs, never JPA entities")
                .check(CLASSES);
    }

    private static String pkg(String module, String layer) {
        return ROOT + "." + module + "." + layer + "..";
    }
}
