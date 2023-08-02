package com.funixproductions.api.user.service.ressources;

import com.funixproductions.api.encryption.client.clients.FunixProductionsEncryptionClient;
import com.funixproductions.api.google.auth.client.clients.InternalGoogleAuthClient;
import com.funixproductions.api.google.recaptcha.client.services.GoogleRecaptchaHandler;
import com.funixproductions.api.user.client.dtos.UserDTO;
import com.funixproductions.api.user.client.dtos.UserTokenDTO;
import com.funixproductions.api.user.client.dtos.requests.UserCreationDTO;
import com.funixproductions.api.user.client.dtos.requests.UserLoginDTO;
import com.funixproductions.api.user.client.enums.UserRole;
import com.funixproductions.api.user.service.components.UserTestComponent;
import com.funixproductions.api.user.service.entities.User;
import com.funixproductions.api.user.service.repositories.UserTokenRepository;
import com.funixproductions.api.user.service.services.UserTokenService;
import com.funixproductions.core.crud.dtos.PageDTO;
import com.funixproductions.core.exceptions.ApiNotFoundException;
import com.funixproductions.core.test.beans.JsonHelper;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.lang.reflect.Type;
import java.util.UUID;

import static com.funixproductions.api.user.service.components.UserTestComponent.USER_PASSWORD;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;

@SpringBootTest
@AutoConfigureMockMvc
@RunWith(MockitoJUnitRunner.class)
class TestUserAuthResource {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserTestComponent userTestComponent;

    @Autowired
    private JsonHelper jsonHelper;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private UserTokenService userTokenService;

    @MockBean
    private GoogleRecaptchaHandler googleCaptchaService;

    @MockBean
    private FunixProductionsEncryptionClient funixProductionsEncryptionClient;

    @MockBean
    private InternalGoogleAuthClient googleAuthClient;

    @BeforeEach
    void setupMocks() {
        Mockito.doNothing().when(googleCaptchaService).verify(ArgumentMatchers.any(), ArgumentMatchers.anyString());
        Mockito.when(funixProductionsEncryptionClient.encrypt(ArgumentMatchers.anyString())).thenReturn(UUID.randomUUID().toString());
        Mockito.when(funixProductionsEncryptionClient.decrypt(ArgumentMatchers.anyString())).thenReturn(USER_PASSWORD);
        Mockito.doNothing().when(googleAuthClient).deleteAllByUserUuidIn(anyList());
    }

    @Test
    void testRegisterSuccess() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserDTO userDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserDTO.class);
        assertEquals(creationDTO.getUsername(), userDTO.getUsername());
        assertEquals(creationDTO.getEmail(), userDTO.getEmail());
        assertEquals(UserRole.USER, userDTO.getRole());
    }

    @Test
    void testRegisterSpecialsChars() throws Exception {
        registerFailTest("tes##");
        registerFailTest("!tes##");
        registerFailTest("!tes ##");
        registerFailTest("tes/");
        registerFailTest("tes ");
        registerFailTest("oui fi");
    }

    @Test
    void testRegisterValidUsername() throws Exception {
        registerSuccessTest("bonjourFunix");
        registerSuccessTest("bonjourFunix22");
        registerSuccessTest("WHATHE");
        registerSuccessTest("bonjour-Funix22");
        registerSuccessTest("bonjour_Funix22");
    }

    @Test
    void testGetActualSessions() throws Exception {
        this.userTokenRepository.deleteAll();

        final User user = userTestComponent.createBasicUser();
        final UserTokenDTO userTokenDTO = userTestComponent.loginUser(user);
        final User adminUser = userTestComponent.createAdminAccount();
        final Type typeToken = new TypeToken<PageDTO<UserTokenDTO>>() {}.getType();

        userTestComponent.loginUser(adminUser);
        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.get("/user/auth/sessions")
                        .header("Authorization", "Bearer " + userTokenDTO.getToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        PageDTO<UserTokenDTO> userTokenDTOS = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), typeToken);
        assertEquals(1, userTokenDTOS.getTotalElementsThisPage());
        assertEquals(userTokenDTO, userTokenDTOS.getContent().get(0));

        userTestComponent.loginUser(user);

        mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.get("/user/auth/sessions")
                        .header("Authorization", "Bearer " + userTokenDTO.getToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();
        userTokenDTOS = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), typeToken);
        assertEquals(2, userTokenDTOS.getTotalElementsThisPage());
    }

    @Test
    void testRemoveSessionToken() throws Exception {
        this.userTokenRepository.deleteAll();
        final User user = userTestComponent.createBasicUser();
        final User adminUser = userTestComponent.createAdminAccount();
        final UserTokenDTO userTokenDTO = userTestComponent.loginUser(user);
        final UserTokenDTO userTokenDTO2 = userTestComponent.loginUser(user);
        final UserTokenDTO adminTokenDTO = userTestComponent.loginUser(adminUser);

        this.mockMvc.perform(MockMvcRequestBuilders.delete("/user/auth/sessions")
                        .header("Authorization", "Bearer " + adminTokenDTO.getToken()))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();

        this.mockMvc.perform(MockMvcRequestBuilders.delete("/user/auth/sessions?id=" + userTokenDTO.getId() + "&id=" + userTokenDTO2.getId() + "&id=" + adminTokenDTO.getId())
                        .header("Authorization", "Bearer " + adminTokenDTO.getToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        assertDoesNotThrow(() -> this.userTokenService.findById(adminTokenDTO.getId().toString()));
        assertThrowsExactly(ApiNotFoundException.class, () -> {
            this.userTokenService.findById(userTokenDTO.getId().toString());
            this.userTokenService.findById(userTokenDTO2.getId().toString());
        });
    }

    private void registerSuccessTest(final String username) throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(username);
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserDTO userDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserDTO.class);
        assertEquals(creationDTO.getUsername(), userDTO.getUsername());
        assertEquals(creationDTO.getEmail(), userDTO.getEmail());
        assertEquals(UserRole.USER, userDTO.getRole());
    }

    private void registerFailTest(final String username) throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(username);
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testLogout() throws Exception {
        final User account = userTestComponent.createBasicUser();

        final UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsername(account.getUsername());
        loginDTO.setPassword(USER_PASSWORD);
        loginDTO.setStayConnected(false);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(loginDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserTokenDTO tokenDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserTokenDTO.class);
        assertEquals(tokenDTO.getUser().getId(), account.getUuid());
        Assertions.assertNotNull(tokenDTO.getToken());
        Assertions.assertNotNull(tokenDTO.getExpirationDate());

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenDTO.getToken()))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenDTO.getToken()))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void testRegisterWithPasswordTooShort() throws Exception {
        testInvalidPasswordRegister("1");
    }

    @Test
    void testRegisterWithPasswordTooShort2() throws Exception {
        testInvalidPasswordRegister("12345678");
    }

    @Test
    void testRegisterWithPasswordNotEnoughNumbers() throws Exception {
        testInvalidPasswordRegister("sdkfhlskdqhldskjqfh");
    }

    void testInvalidPasswordRegister(final String password) throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword(password);
        creationDTO.setPasswordConfirmation(password);
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testFunixAdminCreation() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername("funix");
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserDTO userDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserDTO.class);
        assertEquals(creationDTO.getUsername(), userDTO.getUsername());
        assertEquals(creationDTO.getEmail(), userDTO.getEmail());
        assertEquals(UserRole.ADMIN, userDTO.getRole());

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testRegisterFailPasswordsMismatch() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AAsssdd");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testRegisterUsernameTaken() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testRegisterUsernameTakenNoCase() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername("patrickbalkany");
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(true);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        creationDTO.setUsername("patrickBalkany");
        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testRegisterNoCGV() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(true);
        creationDTO.setAcceptCGV(false);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testRegisterNoCGU() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(false);
        creationDTO.setAcceptCGV(true);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testRegisterNoCGUAndNoCGV() throws Exception {
        final UserCreationDTO creationDTO = new UserCreationDTO();
        creationDTO.setEmail(UUID.randomUUID() + "@gmail.com");
        creationDTO.setUsername(UUID.randomUUID().toString());
        creationDTO.setPassword("ousddffdi22AA");
        creationDTO.setPasswordConfirmation("ousddffdi22AA");
        creationDTO.setAcceptCGU(false);
        creationDTO.setAcceptCGV(false);

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(creationDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testLoginSuccess() throws Exception {
        final User account = userTestComponent.createBasicUser();

        final UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsername(account.getUsername());
        loginDTO.setPassword(USER_PASSWORD);
        loginDTO.setStayConnected(false);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(loginDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserTokenDTO tokenDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserTokenDTO.class);
        assertEquals(tokenDTO.getUser().getId(), account.getUuid());
        Assertions.assertNotNull(tokenDTO.getToken());
        Assertions.assertNotNull(tokenDTO.getExpirationDate());
    }

    @Test
    void testLoginSuccessNoExpiration() throws Exception {
        final User account = userTestComponent.createBasicUser();

        final UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsername(account.getUsername());
        loginDTO.setPassword(USER_PASSWORD);
        loginDTO.setStayConnected(true);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(loginDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserTokenDTO tokenDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserTokenDTO.class);
        assertEquals(tokenDTO.getUser().getId(), account.getUuid());
        Assertions.assertNotNull(tokenDTO.getToken());
        Assertions.assertNull(tokenDTO.getExpirationDate());
    }

    @Test
    void testLoginWrongPassword() throws Exception {
        final UserLoginDTO loginDTO = new UserLoginDTO();

        loginDTO.setUsername("JENEXISTEPAS");
        loginDTO.setPassword("CEMOTDEPASSENONPLUS");

        this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(loginDTO)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testGetCurrentUser() throws Exception {
        final User account = userTestComponent.createBasicUser();

        final UserLoginDTO loginDTO = new UserLoginDTO();
        loginDTO.setUsername(account.getUsername());
        loginDTO.setPassword(USER_PASSWORD);
        loginDTO.setStayConnected(true);

        MvcResult mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.post("/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonHelper.toJson(loginDTO)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn();

        final UserTokenDTO tokenDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserTokenDTO.class);

        mvcResult = this.mockMvc.perform(MockMvcRequestBuilders.get("/user/auth/current")
                .header("Authorization", "Bearer " + tokenDTO.getToken())
        ).andExpect(MockMvcResultMatchers.status().isOk()).andReturn();
        final UserDTO userDTO = jsonHelper.fromJson(mvcResult.getResponse().getContentAsString(), UserDTO.class);

        assertEquals(tokenDTO.getUser().getUsername(), userDTO.getUsername());
        assertEquals(tokenDTO.getUser().getEmail(), userDTO.getEmail());
        assertEquals(tokenDTO.getUser().getRole(), userDTO.getRole());
        assertEquals(tokenDTO.getUser().getId(), userDTO.getId());
    }

    @Test
    void testFailGetCurrentUserNoAuth() throws Exception {
        this.mockMvc.perform(MockMvcRequestBuilders.get("/user/auth/current")).andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void testFailGetCurrentUserBadAuth() throws Exception {
        this.mockMvc.perform(MockMvcRequestBuilders.get("/user/auth/current")
                        .header("Authorization", "Bearer " + "BADTOKEN"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

}
