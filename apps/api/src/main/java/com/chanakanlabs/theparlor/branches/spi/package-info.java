/**
 * Contracts that modules owning branch commitments implement, so branches can derive table state
 * and guard configuration changes without depending on those modules.
 */
@org.springframework.modulith.NamedInterface("spi")
package com.chanakanlabs.theparlor.branches.spi;
