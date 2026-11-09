package dev.settle.group;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class GroupWebConfig implements WebMvcConfigurer {

    private final GroupAccess access;

    GroupWebConfig(GroupAccess access) {
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GroupMembershipInterceptor(access))
                .addPathPatterns("/groups/{" + GroupMembershipInterceptor.GROUP_ID_VARIABLE + "}/**");
    }
}
