package com.chanakanlabs.bgstore.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chanakanlabs.bgstore.contract.model.HelloResponse.DatabaseEnum;
import com.chanakanlabs.bgstore.contract.model.HelloResponse.ServiceEnum;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HelloControllerTest {

  @Test
  void returnsHelloAfterDatabaseProbeSucceeds() {
    var database = mock(JdbcTemplate.class);
    when(database.queryForObject("select 1", Integer.class)).thenReturn(1);

    var response = new HelloController(database).getHello();

    verify(database).queryForObject(eq("select 1"), eq(Integer.class));
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Hello, BGStore!");
    assertThat(response.getBody().getService()).isEqualTo(ServiceEnum.BGSTORE_API);
    assertThat(response.getBody().getDatabase()).isEqualTo(DatabaseEnum.CONNECTED);
  }
}
