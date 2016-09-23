package ru.ratauth.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.restdocs.payload.JsonFieldType
import ru.ratauth.interaction.AuthzResponseType
import ru.ratauth.interaction.GrantType
import ru.ratauth.server.local.PersistenceServiceStubConfiguration
import ru.ratauth.server.local.ProvidersStubConfiguration

import static com.jayway.restassured.RestAssured.given
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.isEmptyOrNullString
import static org.hamcrest.core.IsNot.not
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document
/**
 * @author djassan
 * @since 11/09/16
 */
class RegistrationAPISpec extends BaseDocumentationSpec {
  @Value('${server.port}')
  String port
  @Autowired
  ObjectMapper objectMapper

  def 'should request registration code over provider channel'() {
    given:
    def setup = given(this.documentationSpec)
      .accept(ContentType.URLENC)
      .filter(document('reg_code_provider_channel_succeed',
      requestParameters(
        parameterWithName('client_id')
          .description('relying party identifier'),
        parameterWithName('scope')
          .description('Scope for authorization that will be provided through JWT to all resource servers in flow'),
        parameterWithName(ProvidersStubConfiguration.REG_CREDENTIAL)
          .description('Some credential fields...')
          .optional()
      ),
      responseFields(
        fieldWithPath('status')
          .description('registration status(SUCCESS,NEED_APPROVAL)')
          .type(JsonFieldType.STRING),
        fieldWithPath('data')
          .description('some object data that contains user specific data, e.g.: username')
          .type(JsonFieldType.OBJECT)
      )))
      .given()
      .formParam('client_id', PersistenceServiceStubConfiguration.CLIENT_NAME)
      .formParam('scope', 'rs.read')
      .formParam(ProvidersStubConfiguration.REG_CREDENTIAL, 'credential')
    when:
    def result = setup
      .when()
      .post("register")
    then:
    result
      .then()
      .statusCode(HttpStatus.OK.value())
      .body("data.username", containsString("login"))
  }

  def 'should successfully finish registration over provider channel'() {
    given:
    def setup = given(this.documentationSpec)
      .accept(ContentType.URLENC)
      .filter(document('reg_code_finish_provider_channel_succeed',
      requestParameters(
        parameterWithName('code')
          .description('registration code provided by user for second'),
        parameterWithName('username')
          .description('Some user identifier got from first step in data object, e.g.: username')
          .optional(),
        parameterWithName('response_type')
          .description('Response type that must be provided'),
        parameterWithName('grant_type')
          .description('grant type for token request'),
        parameterWithName('scope')
          .description('Scope for authorization that will be provided through JWT to all resource servers in flow'),
      ),
      requestHeaders(
        headerWithName(HttpHeaders.AUTHORIZATION)
          .description('Authorization header for relying party basic authorization')
      ),
      responseFields(
        fieldWithPath('access_token')
          .description('Access token')
          .type(JsonFieldType.STRING),
        fieldWithPath('refresh_token')
          .description('token that can be used to refresh expired access token')
          .type(JsonFieldType.STRING),
        fieldWithPath('token_type')
          .description('type of auth token, e.g.: BEARER')
          .type(JsonFieldType.STRING),
        fieldWithPath('id_token')
          .description('JWT token')
          .type(JsonFieldType.STRING),
        fieldWithPath('expires_in')
          .description('expiration date of access token')
          .type(JsonFieldType.NUMBER),
        fieldWithPath('client_id')
          .description('identifier of relying party')
          .type(JsonFieldType.STRING)
      )))
      .given()
      .formParam('code', ProvidersStubConfiguration.REG_CODE)
      .formParam('username', 'login')
      .formParam('scope', 'read')
      .formParam('grant_type', GrantType.AUTHORIZATION_CODE.name())
      .formParam('response_type', AuthzResponseType.TOKEN.name())
      .header(IntegrationSpecUtil.createAuthHeaders(PersistenceServiceStubConfiguration.CLIENT_NAME,
                                                    PersistenceServiceStubConfiguration.PASSWORD))
    when:
    def result = setup
      .when()
      .post("register")
    then:
    result
      .then()
      .statusCode(HttpStatus.OK.value())
      .body("access_token", not(isEmptyOrNullString()))
      .body("refresh_token", not(isEmptyOrNullString()))
      .body("id_token", not(isEmptyOrNullString()))
      .body("expires_in", not(isEmptyOrNullString()))
  }

}