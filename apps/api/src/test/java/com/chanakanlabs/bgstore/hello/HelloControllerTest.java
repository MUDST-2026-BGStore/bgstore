package com.chanakanlabs.bgstore.hello;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanakanlabs.bgstore.contract.model.HelloResponse.DatabaseEnum;
import com.chanakanlabs.bgstore.contract.model.HelloResponse.ServiceEnum;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class HelloControllerTest {

  @Test
  void returnsHelloAfterDatabaseProbeSucceeds() {
    DSLContext database =
        DSL.using(
            new MockConnection(
                context -> {
                  var contextDsl = DSL.using(SQLDialect.POSTGRES);
                  var field = DSL.field("result", Integer.class);
                  var result = contextDsl.newResult(field);
                  var record = contextDsl.newRecord(field);
                  record.value1(1);
                  result.add(record);
                  return new MockResult[] {new MockResult(1, result)};
                }),
            SQLDialect.POSTGRES);

    var response = new HelloController(database).getHello();

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Hello, BGStore!");
    assertThat(response.getBody().getService()).isEqualTo(ServiceEnum.BGSTORE_API);
    assertThat(response.getBody().getDatabase()).isEqualTo(DatabaseEnum.CONNECTED);
  }
}
