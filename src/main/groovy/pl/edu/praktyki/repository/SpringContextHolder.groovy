package pl.edu.praktyki.repository

import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext
    }

    static <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass)
    }
}

