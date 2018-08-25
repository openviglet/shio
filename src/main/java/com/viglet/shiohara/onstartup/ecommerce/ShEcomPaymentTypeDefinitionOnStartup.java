package com.viglet.shiohara.onstartup.ecommerce;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.shiohara.persistence.model.ecommerce.ShEcomPaymentTypeDefinition;
import com.viglet.shiohara.persistence.repository.ecommerce.ShEcomPaymentTypeDefinitionRepository;

@Component
public class ShEcomPaymentTypeDefinitionOnStartup {
	@Autowired
	private ShEcomPaymentTypeDefinitionRepository shEcomPaymentTypeDefinitionRepository;

	public void createDefaultRows() {

		if (shEcomPaymentTypeDefinitionRepository.findAll().isEmpty()) {
			ShEcomPaymentTypeDefinition shEcomPaymentTypeDefinition = new ShEcomPaymentTypeDefinition();
			shEcomPaymentTypeDefinition.setId("983707eb-c84d-4432-9a7c-72101ec2f3a2");
			shEcomPaymentTypeDefinition.setName(ShSystemEcomPaymentTypeDefinition.REDECARD);
			shEcomPaymentTypeDefinition.setDate(new Date());
			shEcomPaymentTypeDefinition.setDescription("Rede Card Payment");
			shEcomPaymentTypeDefinition.setClassName("com.viglet.shiohara.ecommerce.payment.ShRedeCardPayment");
			shEcomPaymentTypeDefinition
					.setSettingPath("template/ecommerce/payment/rede-card/setting/rede-card-setting.html");
			shEcomPaymentTypeDefinition.setFormPath("widget/payment/rede-card/rede-card-form");

			shEcomPaymentTypeDefinitionRepository.save(shEcomPaymentTypeDefinition);

			shEcomPaymentTypeDefinition = new ShEcomPaymentTypeDefinition();
			shEcomPaymentTypeDefinition.setId("9e3ce17e-7fe8-44a8-a9a0-a302202b6ead");
			shEcomPaymentTypeDefinition.setName(ShSystemEcomPaymentTypeDefinition.PAYMENTSLIP);
			shEcomPaymentTypeDefinition.setDate(new Date());
			shEcomPaymentTypeDefinition.setDescription("Payment Slip");
			shEcomPaymentTypeDefinition.setClassName("com.viglet.shiohara.ecommerce.payment.ShPaymentSlip");
			shEcomPaymentTypeDefinition
					.setSettingPath("template/ecommerce/payment/payment-slip/setting/payment-slip-setting.html");
			shEcomPaymentTypeDefinition.setFormPath("widget/payment/payment-slip/payment-slip-form");

			shEcomPaymentTypeDefinitionRepository.save(shEcomPaymentTypeDefinition);

			shEcomPaymentTypeDefinition = new ShEcomPaymentTypeDefinition();
			shEcomPaymentTypeDefinition.setId("a0a81e69-04f9-4fe6-a313-2c643e467a5e");
			shEcomPaymentTypeDefinition.setName(ShSystemEcomPaymentTypeDefinition.SHOPLINE);
			shEcomPaymentTypeDefinition.setDate(new Date());
			shEcomPaymentTypeDefinition.setDescription("Shopline Payment");
			shEcomPaymentTypeDefinition.setClassName("com.viglet.shiohara.ecommerce.payment.ShShoplinePayment");
			shEcomPaymentTypeDefinition
					.setSettingPath("template/ecommerce/payment/shopline/setting/shopline-setting.html");
			shEcomPaymentTypeDefinition.setFormPath("widget/payment/shopline/shopline-form");

			shEcomPaymentTypeDefinitionRepository.save(shEcomPaymentTypeDefinition);

		}
	}

}
