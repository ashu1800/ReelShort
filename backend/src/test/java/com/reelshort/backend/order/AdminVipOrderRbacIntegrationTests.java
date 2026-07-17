package com.reelshort.backend.order;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminPermission;
import com.reelshort.backend.admin.AdminPermissionRepository;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminRole;
import com.reelshort.backend.admin.AdminRoleRepository;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.system.security.TotpService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminVipOrderRbacIntegrationTests {
	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private AdminPermissionRepository permissions;
	@Autowired private AdminRoleRepository roles;
	@Autowired private AdminUserRepository admins;
	@Autowired private PasswordHasher passwordHasher;
	@Autowired private TotpService totpService;

	@MockitoBean
	private VipOrderService vipOrderService;

	@Test
	void writeOnlyCanMutateWhileReadOnlyCannotConfirmOrReject() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminUser writer = admin("vip-writer-" + suffix, AdminPermissions.ORDER_WRITE, true);
		AdminUser reader = admin("vip-reader-" + suffix, AdminPermissions.ORDER_READ, false);
		String writerCode = totpService.generateCurrentCode(writer.totpSecret());
		String writerToken = login(writer.username(), writerCode);
		String readerToken = login(reader.username(), null);
		UUID confirmId = UUID.randomUUID();
		UUID rejectId = UUID.randomUUID();
		String txHash = "a".repeat(64);
		String code = totpService.generateCurrentCode(writer.totpSecret());
		VipOrder confirmed = order("VIP-rbac-confirm-" + suffix);
		confirmed.confirm(txHash, writer.username());
		VipOrder rejected = order("VIP-rbac-reject-" + suffix);
		rejected.reject(writer.username());
		when(vipOrderService.manualConfirm(eq(confirmId), eq(txHash), eq(writer.username()))).thenReturn(confirmed);
		when(vipOrderService.reject(eq(rejectId), eq(writer.username()))).thenReturn(rejected);

		mockMvc.perform(post("/api/admin/vip/orders/{id}/confirm", confirmId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + writerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"txHash\":\"%s\",\"totpCode\":\"%s\"}".formatted(txHash, code)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/admin/vip/orders/{id}/reject", rejectId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + writerToken))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/admin/vip/orders/{id}/confirm", confirmId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"txHash\":\"%s\",\"totpCode\":\"000000\"}".formatted(txHash)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/vip/orders/{id}/reject", rejectId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken))
				.andExpect(status().isForbidden());
	}

	private AdminUser admin(String username, String permissionCode, boolean totp) {
		AdminPermission permission = permissions.findByCode(permissionCode).orElseThrow();
		AdminRole role = AdminRole.create("ROLE-" + username, username);
		role.grant(permission);
		roles.save(role);
		AdminUser admin = AdminUser.create(username, passwordHasher.hash("VipAdmin123"), AdminUserStatus.ACTIVE);
		admin.assignRole(role);
		if (totp) {
			admin.enableTotp("JBSWY3DPEHPK3PXP");
		}
		return admins.save(admin);
	}

	private String login(String username, String totpCode) throws Exception {
		String totpJson = totpCode == null ? "" : ",\"totpCode\":\"" + totpCode + "\"";
		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"%s\",\"password\":\"VipAdmin123\"%s}"
						.formatted(username, totpJson)))
				.andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
	}

	private VipOrder order(String orderNo) {
		return VipOrder.create(UUID.randomUUID(), orderNo, new BigDecimal("15"), 1, 20,
				"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
	}
}
