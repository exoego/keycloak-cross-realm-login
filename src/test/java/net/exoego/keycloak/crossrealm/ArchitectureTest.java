package net.exoego.keycloak.crossrealm;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakContext;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("net.exoego.keycloak.crossrealm");

    @Test
    void onlyHomeRealmContextSetsTheContextRealm() {
        noClasses().that().doNotBelongToAnyOf(HomeRealmContext.class)
                .should().accessTargetWhere(describe("it sets the context realm",
                        access -> access.getTargetOwner().isAssignableTo(KeycloakContext.class)
                                && access.getName().equals("setRealm")))
                .because("HomeRealmContext puts the old realm back, and nothing else does")
                .check(MAIN);
    }
}
