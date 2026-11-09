package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GroupRoutesGuardTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyRouteNestedUnderAGroupNamesItsGroupIdSoTheMembershipCheckCannotBeSkipped() {
        List<String> nestedRoutes = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            patterns.stream().filter(p -> p.startsWith("/groups/")).forEach(nestedRoutes::add);
        }

        assertThat(nestedRoutes.size()).isGreaterThanOrEqualTo(11);
        assertThat(nestedRoutes).allSatisfy(route ->
                assertThat(route).as("route %s", route).startsWith("/groups/{groupId}"));
    }

    @Test
    void theOnlyGroupRoutesWithoutAGroupIdAreCreatingAndListing() {
        List<String> topLevel = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            info.getPathPatternsCondition().getPatternValues().stream()
                    .filter(p -> p.equals("/groups"))
                    .forEach(topLevel::add);
        }

        assertThat(topLevel).hasSize(2);
    }
}
