package com.example.walletledger.adapters;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.Slice;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private static final String ROOT_PACKAGE = "com.example.walletledger";
    private static final String DOMAIN_MODULE_PACKAGE = "com.example.walletledger.core..";
    private static final String APPLICATION_MODULE_PACKAGE = "com.example.walletledger.usecases..";
    private static final String ADAPTER_MODULE_PACKAGE = "com.example.walletledger.adapters..";
    private static final String JDK_PACKAGE = "java..";

    private static final String INBOUND_PORT_PACKAGE = "..usecases.api..";
    private static final String OUTBOUND_PORT_PACKAGE = "..usecases.spi..";
    private static final String APPLICATION_SERVICE_PACKAGE = "..usecases.service..";

    private static final String INBOUND_ADAPTER_PACKAGE = "..adapters.web..";
    private static final String WEB_PAYLOAD_PACKAGE = "..adapters.web.dto..";
    private static final String PERSISTENCE_ADAPTER_PACKAGE = "..adapters.persistence..";
    private static final String SYSTEM_ADAPTER_PACKAGE = "..adapters.system..";
    private static final String COMPOSITION_ROOT_PACKAGE = "..adapters.config..";
    private static final String ADAPTER_SLICE_PATTERN = "com.example.walletledger.adapters.(*)..";
    private static final String COMPOSITION_ROOT_SLICE_NAME = "config";

    private static final String DOMAIN_LAYER = "domain";
    private static final String APPLICATION_LAYER = "application";
    private static final String ADAPTER_LAYER = "adapters";

    private static final String INBOUND_PORT_SUFFIX = "ApiPort";
    private static final String OUTBOUND_PORT_SUFFIX = "SpiPort";
    private static final String TRANSACTION_SEAM_NAME = "LedgerWorkspace";
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String SERVICE_SUFFIX = "Service";
    private static final String REQUEST_SUFFIX = "Request";
    private static final String RESPONSE_SUFFIX = "Response";
    private static final String GENERATED_MAPPER_SUFFIX = "MapperImpl";

    private static final String IN_MEMORY_PREFIX = "InMemory";
    private static final String CAFFEINE_PREFIX = "Caffeine";
    private static final String ADAPTER_SUFFIX = "Adapter";
    private static final String STORE_SUFFIX = "Store";
    private static final String REPOSITORY_SUFFIX = "Repository";

    private static final String LEGACY_DATE_TYPE = "java.util.Date";
    private static final String JDBC_PACKAGE = "java.sql..";
    private static final String JDBC_EXTENSION_PACKAGE = "javax.sql..";

    private static final String[] FRAMEWORK_PACKAGES = {
            "org.springframework..",
            "jakarta..",
            "javax..",
            "com.fasterxml.jackson..",
            "io.micrometer..",
            "org.mapstruct..",
            "com.github.benmanes..",
            "io.github.bucket4j..",
            "io.swagger..",
            "lombok.."
    };

    private static final DescribedPredicate<JavaClass> CONCRETE_STORAGE_TYPES =
            simpleNameStartingWith(IN_MEMORY_PREFIX)
                    .or(simpleNameStartingWith(CAFFEINE_PREFIX))
                    .or(simpleNameEndingWith(ADAPTER_SUFFIX))
                    .or(simpleNameEndingWith(STORE_SUFFIX))
                    .or(simpleNameEndingWith(REPOSITORY_SUFFIX))
                    .as("concrete storage types");

    private static final DescribedPredicate<Slice> COMPOSITION_ROOT_SLICE = DescribedPredicate.describe(
            "the composition root",
            slice -> COMPOSITION_ROOT_SLICE_NAME.equals(slice.getNamePart(1)));

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT_PACKAGE);
    }

    @Test
    void shouldNotDependOnSpringWhenClassResidesInTheDomainModule() {
        // given
        var rule = noClasses().that().resideInAPackage(DOMAIN_MODULE_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
                .as("domain_should_not_depend_on_spring")
                .because("the core module is framework free and compiles without Spring on the classpath");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNotDependOnAdaptersWhenClassResidesInTheDomainModule() {
        // given
        var rule = noClasses().that().resideInAPackage(DOMAIN_MODULE_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_MODULE_PACKAGE)
                .as("domain_should_not_depend_on_adapters")
                .because("the dependency direction is outer to inner and never the reverse");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldDependOnTheJdkOnlyWhenClassResidesInTheDomainModule() {
        // given
        var rule = noClasses().that().resideInAPackage(DOMAIN_MODULE_PACKAGE)
                .should().dependOnClassesThat().resideOutsideOfPackages(DOMAIN_MODULE_PACKAGE, JDK_PACKAGE)
                .as("domain_should_depend_on_the_jdk_only")
                .because("the core module carries no runtime dependency at all");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNotDependOnSpringWhenClassResidesInTheApplicationModule() {
        // given
        var rule = noClasses().that().resideInAPackage(APPLICATION_MODULE_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
                .as("application_should_not_depend_on_spring")
                .because("use cases are plain Java and are exercised without an application context");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNotDependOnAdaptersWhenClassResidesInTheApplicationModule() {
        // given
        var rule = noClasses().that().resideInAPackage(APPLICATION_MODULE_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(ADAPTER_MODULE_PACKAGE)
                .as("application_should_not_depend_on_adapters")
                .because("the application module owns the ports and knows no implementation of them");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldDependOnTheDomainAndTheJdkOnlyWhenClassResidesInTheApplicationModule() {
        // given
        var rule = noClasses().that().resideInAPackage(APPLICATION_MODULE_PACKAGE)
                .should().dependOnClassesThat()
                .resideOutsideOfPackages(DOMAIN_MODULE_PACKAGE, APPLICATION_MODULE_PACKAGE, JDK_PACKAGE)
                .as("application_should_depend_on_domain_only")
                .because("the usecases module sees the core module and the JDK, and nothing else");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldPointInwardsWhenModuleDependenciesAreCompared() {
        // given
        var rule = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer(DOMAIN_LAYER).definedBy(DOMAIN_MODULE_PACKAGE)
                .layer(APPLICATION_LAYER).definedBy(APPLICATION_MODULE_PACKAGE)
                .layer(ADAPTER_LAYER).definedBy(ADAPTER_MODULE_PACKAGE)
                .whereLayer(ADAPTER_LAYER).mayNotBeAccessedByAnyLayer()
                .whereLayer(APPLICATION_LAYER).mayOnlyBeAccessedByLayers(ADAPTER_LAYER)
                .whereLayer(DOMAIN_LAYER).mayOnlyBeAccessedByLayers(APPLICATION_LAYER, ADAPTER_LAYER)
                .as("modules_should_point_inwards")
                .because("core, usecases and adapters form an onion whose arrows all point to the centre");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNotReachOutboundAdaptersWhenClassResidesInTheInboundAdapter() {
        // given
        var rule = noClasses().that().resideInAPackage(INBOUND_ADAPTER_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(PERSISTENCE_ADAPTER_PACKAGE, SYSTEM_ADAPTER_PACKAGE)
                .as("adapters_should_be_independent")
                .because("inbound and outbound adapters meet only through the ports of the application module");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNotReachTheInboundAdapterWhenClassResidesInAnOutboundAdapter() {
        // given
        var rule = noClasses().that()
                .resideInAnyPackage(PERSISTENCE_ADAPTER_PACKAGE, SYSTEM_ADAPTER_PACKAGE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(INBOUND_ADAPTER_PACKAGE, COMPOSITION_ROOT_PACKAGE)
                .as("outbound_adapters_should_not_depend_on_inbound_adapters")
                .because("a driven adapter is replaceable only if it is ignorant of what drives the application");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNameNoPersistenceTypeWhenClassIsNotTheCompositionRoot() {
        // given
        var rule = noClasses().that()
                .resideOutsideOfPackages(PERSISTENCE_ADAPTER_PACKAGE, COMPOSITION_ROOT_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(PERSISTENCE_ADAPTER_PACKAGE)
                .as("only_the_composition_root_may_name_persistence_types")
                .because("swapping the storage engine must touch the wiring and nothing else");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldBeFreeOfCyclesWhenAdapterPackagesAreCompared() {
        // given
        var rule = slices().matching(ADAPTER_SLICE_PATTERN)
                .that(DescribedPredicate.not(COMPOSITION_ROOT_SLICE))
                .should().beFreeOfCycles()
                .as("adapter_packages_should_be_free_of_cycles")
                .because("every adapter is independently replaceable, which a cycle would prevent");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldBeInterfacesWhenTypesArePortsOfTheApplication() {
        // given
        var rule = classes().that().resideInAnyPackage(INBOUND_PORT_PACKAGE, OUTBOUND_PORT_PACKAGE)
                .and().areNotRecords()
                .should().beInterfaces()
                .as("ports_should_be_interfaces")
                .because("dependency inversion needs a contract, and commands and results are records");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldCarryTheApiPortSuffixWhenInterfaceResidesInTheInboundPortPackage() {
        // given
        var rule = classes().that().resideInAPackage(INBOUND_PORT_PACKAGE).and().areInterfaces()
                .should().haveSimpleNameEndingWith(INBOUND_PORT_SUFFIX)
                .as("inbound_ports_should_carry_the_api_port_suffix")
                .because("the clean_hex variant names every driving port consistently");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldResideInTheInboundPortPackageWhenNameEndsWithApiPort() {
        // given
        var rule = classes().that().haveSimpleNameEndingWith(INBOUND_PORT_SUFFIX)
                .should().beInterfaces()
                .andShould().resideInAPackage(INBOUND_PORT_PACKAGE)
                .as("api_port_suffix_implies_the_inbound_port_package")
                .because("driving contracts are declared in one place and are never classes");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldResideInTheOutboundPortPackageWhenNameEndsWithSpiPort() {
        // given
        var rule = classes().that().haveSimpleNameEndingWith(OUTBOUND_PORT_SUFFIX)
                .or().haveSimpleName(TRANSACTION_SEAM_NAME)
                .should().beInterfaces()
                .andShould().resideInAPackage(OUTBOUND_PORT_PACKAGE)
                .as("outbound_ports_should_be_application_owned_interfaces")
                .because("a replaceable seam is an interface owned by the module that calls it");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldResideInTheAdapterModuleWhenClassImplementsAnOutboundPort() {
        // given
        var rule = classes().that().implement(resideInAPackage(OUTBOUND_PORT_PACKAGE))
                .and().areNotRecords()
                .should().resideInAPackage(ADAPTER_MODULE_PACKAGE)
                .as("outbound_ports_should_only_be_implemented_by_adapters")
                .because("every driven contract is satisfied by an adapter and by nothing else");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldImplementAnInboundPortWhenClassIsAnApplicationService() {
        // given
        var rule = classes().that().resideInAPackage(APPLICATION_SERVICE_PACKAGE)
                .and().haveSimpleNameEndingWith(SERVICE_SUFFIX)
                .should().implement(resideInAPackage(INBOUND_PORT_PACKAGE))
                .as("use_case_services_should_implement_an_inbound_port")
                .because("a use case is reachable only through the port that declares it");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldNameNoConcreteStorageTypeWhenClassResidesInsideTheHexagon() {
        // given
        var rule = noClasses().that()
                .resideInAnyPackage(DOMAIN_MODULE_PACKAGE, APPLICATION_MODULE_PACKAGE)
                .should().dependOnClassesThat(CONCRETE_STORAGE_TYPES)
                .as("hexagon_should_not_name_concrete_storage_types")
                .because("the application knows the storage seam only by its port interface");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldResideInTheInboundAdapterWhenNameEndsWithController() {
        // given
        var rule = classes().that().haveSimpleNameEndingWith(CONTROLLER_SUFFIX)
                .should().resideInAPackage(INBOUND_ADAPTER_PACKAGE)
                .as("controllers_should_reside_in_the_inbound_adapter")
                .because("HTTP is one inbound adapter and lives in one place");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldResideInTheWebPayloadPackageWhenNameEndsWithRequestOrResponse() {
        // given
        var rule = classes().that().haveSimpleNameEndingWith(REQUEST_SUFFIX)
                .or().haveSimpleNameEndingWith(RESPONSE_SUFFIX)
                .should().resideInAPackage(WEB_PAYLOAD_PACKAGE)
                .as("web_payloads_should_reside_in_the_web_adapter")
                .because("wire formats belong to the protocol, never to the domain or the use cases");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldInjectCollaboratorsThroughConstructorsWhenClassIsHandWritten() {
        // given
        var rule = noFields().that().areDeclaredInClassesThat()
                .haveSimpleNameNotEndingWith(GENERATED_MAPPER_SUFFIX)
                .should().beAnnotatedWith(Autowired.class)
                .as("collaborators_should_be_injected_through_constructors")
                .because("collaborators are declared as constructor parameters and kept final");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldStayFreeOfJdbcWhenClassBelongsToTheService() {
        // given
        var rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(JDBC_PACKAGE, JDBC_EXTENSION_PACKAGE)
                .as("service_should_not_depend_on_jdbc")
                .because("state lives in memory and no database is used, embedded or otherwise");

        // then
        rule.check(productionClasses);
    }

    @Test
    void shouldUseTheModernTimeApiWhenClassHandlesTimestamps() {
        // given
        var rule = noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName(LEGACY_DATE_TYPE)
                .as("service_should_not_use_the_legacy_date_api")
                .because("instants are modelled with java.time and never with java.util.Date");

        // then
        rule.check(productionClasses);
    }
}
