package com.chanakanlabs.bgstore.hello;

import com.chanakanlabs.bgstore.contract.api.HelloApi;
import com.chanakanlabs.bgstore.contract.model.HelloResponse;
import com.chanakanlabs.bgstore.contract.model.HelloResponse.DatabaseEnum;
import com.chanakanlabs.bgstore.contract.model.HelloResponse.ServiceEnum;
import org.jooq.DSLContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HelloController implements HelloApi {

  private final DSLContext database;

  public HelloController(DSLContext database) {
    this.database = database;
  }

  @Override
  public ResponseEntity<HelloResponse> getHello() {
    database.fetchValue("select 1", Integer.class);

    return ResponseEntity.ok(
        new HelloResponse("Hello, BGStore!", ServiceEnum.BGSTORE_API, DatabaseEnum.CONNECTED));
  }
}
