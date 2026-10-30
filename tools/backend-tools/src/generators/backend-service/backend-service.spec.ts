import { addProjectConfiguration, type Tree } from '@nx/devkit';
import { createTreeWithEmptyWorkspace } from '@nx/devkit/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { backendServiceGenerator } from './backend-service';
import type { BackendServiceGeneratorSchema } from './schema';

const root = 'apps/api/src/main/java/com/chanakanlabs/theparlor/inventory';
const servicePath = `${root}/internal/application/StockPricingService.java`;
const options: BackendServiceGeneratorSchema = {
  module: 'inventory',
  name: 'stock-pricing',
};

function read(tree: Tree, path: string) {
  return tree.read(path)?.toString();
}

describe('backend-service generator', () => {
  let tree: Tree;

  beforeEach(() => {
    tree = createTreeWithEmptyWorkspace();
    addProjectConfiguration(tree, 'api', { root: 'apps/api' });
    tree.write(
      `${root}/package-info.java`,
      '@org.springframework.modulith.ApplicationModule\npackage com.chanakanlabs.theparlor.inventory;\n',
    );
  });

  it('creates a read-only unfinished service inside the existing module', () => {
    const before = tree.listChanges().map((change) => change.path);
    backendServiceGenerator(tree, options);
    const added = tree
      .listChanges()
      .filter((change) => !before.includes(change.path));
    expect(added.map((change) => change.path)).toEqual([servicePath]);
    expect(read(tree, servicePath)).toContain(
      'package com.chanakanlabs.theparlor.inventory.internal.application;',
    );
    expect(read(tree, servicePath)).toContain(
      '@Transactional(readOnly = true)',
    );
    expect(read(tree, servicePath)).toContain('public void execute()');
    expect(read(tree, servicePath)).toContain('TODO(inventory-stock-pricing)');
    expect(read(tree, servicePath)).toContain(
      'throw new UnsupportedOperationException',
    );
    expect(read(tree, servicePath)).not.toContain('@Transactional\n  public');
    expect(read(tree, servicePath)).not.toContain('implements');
  });

  it('creates a public interface and matching transactional command', () => {
    backendServiceGenerator(tree, {
      ...options,
      interfaceName: 'StockPricing',
      method: 'reprice',
      write: true,
    });
    const service = read(tree, servicePath);
    const api = read(tree, `${root}/StockPricing.java`);
    expect(service).toContain(
      'import com.chanakanlabs.theparlor.inventory.StockPricing;',
    );
    expect(service).toContain(
      'public class StockPricingService implements StockPricing',
    );
    expect(service).toContain(
      '@Override\n  @Transactional\n  public void reprice()',
    );
    expect(api).toContain('package com.chanakanlabs.theparlor.inventory;');
    expect(api).toContain('public interface StockPricing');
    expect(api).toContain('void reprice();');
    expect(api).not.toMatch(/^import .*\.internal\./m);
  });

  it('supports PascalCase names without doubling the Service suffix', () => {
    backendServiceGenerator(tree, { ...options, name: 'StockPricingService' });
    expect(tree.exists(servicePath)).toBe(true);
    expect(
      tree.exists(
        `${root}/internal/application/StockPricingServiceService.java`,
      ),
    ).toBe(false);
  });

  it('refuses to overwrite a service and preserves its contents', () => {
    tree.write(servicePath, 'existing service');
    const before = tree.listChanges();
    expect(() =>
      backendServiceGenerator(tree, {
        ...options,
        interfaceName: 'StockPricing',
      }),
    ).toThrow('Refusing to overwrite');
    expect(read(tree, servicePath)).toBe('existing service');
    expect(tree.listChanges()).toEqual(before);
  });

  it('does not stage a partial service when the optional interface already exists', () => {
    tree.write(`${root}/StockPricing.java`, 'existing interface');
    const before = tree.listChanges();
    expect(() =>
      backendServiceGenerator(tree, {
        ...options,
        interfaceName: 'StockPricing',
      }),
    ).toThrow('Refusing to overwrite');
    expect(tree.exists(servicePath)).toBe(false);
    expect(tree.listChanges()).toEqual(before);
  });

  it.each<Partial<BackendServiceGeneratorSchema>>([
    { module: '../inventory' },
    { module: 'inventory/internal' },
    { module: 'Inventory' },
    { module: 'unknown' },
    { module: 'shared' },
    { name: '../escape' },
    { name: 'bad/name' },
    { name: 'Bad; class Injected' },
    { name: '9invalid' },
    { name: 'Service' },
    { name: 'a'.repeat(81) },
    { method: 'return' },
    { method: 'getClass' },
    { method: 'hashCode' },
    { method: 'bad()' },
    { method: 'bad\nmethod' },
    { method: '' },
    { interfaceName: 'bad-interface' },
    { interfaceName: '../Escape' },
    { interfaceName: 'StockPricingService' },
    { interfaceName: '' },
    { interfaceName: 'Service' },
    { interfaceName: 'Override' },
    { interfaceName: 'UnsupportedOperationException' },
  ])('rejects invalid options without changing files: %j', (invalid) => {
    const before = tree.listChanges();
    expect(() =>
      backendServiceGenerator(tree, { ...options, ...invalid }),
    ).toThrow();
    expect(tree.listChanges()).toEqual(before);
  });

  it.each(['contracts', 'shared'])(
    'rejects %s even if it has a module declaration',
    (module) => {
      tree.write(
        `apps/api/src/main/java/com/chanakanlabs/theparlor/${module}/package-info.java`,
        `@org.springframework.modulith.ApplicationModule\npackage com.chanakanlabs.theparlor.${module};\n`,
      );
      expect(() =>
        backendServiceGenerator(tree, { ...options, module }),
      ).toThrow('existing business module');
    },
  );

  it('requires an explicit module declaration rather than silently creating a module', () => {
    tree.write(
      `${root}/package-info.java`,
      'package com.chanakanlabs.theparlor.inventory;',
    );
    expect(() => backendServiceGenerator(tree, options)).toThrow(
      'ApplicationModule',
    );
  });
});
