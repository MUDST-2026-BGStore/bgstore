export interface BackendServiceGeneratorSchema {
  module: string;
  name: string;
  interfaceName?: string;
  method?: string;
  write?: boolean;
}
