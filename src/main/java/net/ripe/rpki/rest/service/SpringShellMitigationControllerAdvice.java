package net.ripe.rpki.rest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
@Order(10000)
@Slf4j
public class SpringShellMitigationControllerAdvice {
    @InitBinder
    public void setAllowedFields(WebDataBinder dataBinder){
        log.info("Spring4Shell: restricting DataBinder fields");
        dataBinder.setDisallowedFields("class.*","Class.*","*.class.*","*.Class.*");
    }
}
