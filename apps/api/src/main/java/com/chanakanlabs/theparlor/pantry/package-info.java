/**
 * The business-wide food and drink menu and each branch's pantry stock. Stock follows the same
 * hold, release and fulfil rules as game sale stock; reservation pre-orders are its only holders.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"contracts::http", "contracts::models", "branches", "shared"})
package com.chanakanlabs.theparlor.pantry;
