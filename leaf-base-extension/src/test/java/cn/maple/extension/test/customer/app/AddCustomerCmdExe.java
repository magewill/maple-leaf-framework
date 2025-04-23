package cn.maple.extension.test.customer.app;

import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.test.customer.app.extension.point.AddCustomerValidatorExtPoint;
import cn.maple.extension.test.customer.app.extension.point.CustomerConvertorExtPoint;
import cn.maple.extension.test.customer.client.AddCustomerCmd;
import cn.maple.extension.test.customer.domain.CustomerEntity;
import cn.maple.extension.test.customer.infrastructure.DomainEventPublisher;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddCustomerCmdExe {
    private final Logger logger = LoggerFactory.getLogger(AddCustomerCmd.class);

    @Resource
    private GXExtensionExecutor extensionExecutor;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    public GXResultUtils<String> execute(AddCustomerCmd cmd) {
        logger.info("Start processing command:" + cmd);

        //validation
        extensionExecutor.executeVoid(AddCustomerValidatorExtPoint.class, cmd.getBizScenario(), extension -> extension.validate(cmd));

        //Convert CO to Entity
        CustomerEntity customerEntity = extensionExecutor.execute(CustomerConvertorExtPoint.class, cmd.getBizScenario(), extension -> extension.clientToEntity(cmd));

        //Call Domain Entity for business logic processing
        logger.info("Call Domain Entity for business logic processing..." + customerEntity);
        customerEntity.addNewCustomer();

        //domainEventPublisher.publish(new CustomerCreatedEvent());
        logger.info("End processing command:" + cmd);
        return GXResultUtils.ok("Success");
    }
}
