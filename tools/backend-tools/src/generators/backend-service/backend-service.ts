import {
  generateFiles,
  joinPathFragments,
  names,
  readProjectConfiguration,
  type Tree,
} from '@nx/devkit';
import { join } from 'node:path';
import type { BackendServiceGeneratorSchema } from './schema';

const forbiddenMethods = new Set([
  ...(
    'abstract assert boolean break byte case catch char class const continue default do double ' +
    'else enum extends final finally float for goto if implements import instanceof int interface ' +
    'long native new package private protected public return short static strictfp super switch ' +
    'synchronized this throw throws transient try void volatile while true false null _'
  ).split(' '),
  // A void/no-argument placeholder cannot override these Object methods safely.
  'clone',
  'equals',
  'finalize',
  'getClass',
  'hashCode',
  'notify',
  'notifyAll',
  'toString',
  'wait',
]);

export function backendServiceGenerator(
  tree: Tree,
  options: BackendServiceGeneratorSchema,
) {
  // Validate before normalizing names: names() alone would silently accept paths or source text.
  if (
    typeof options.module !== 'string' ||
    !/^[a-z][a-z0-9]*$/.test(options.module)
  ) {
    throw new Error(
      'module must be a single lowercase Java package name, such as inventory.',
    );
  }
  if (
    typeof options.name !== 'string' ||
    !/^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$/.test(options.name) ||
    options.name.length > 80
  ) {
    throw new Error(
      'name must contain at most 80 letters, digits or separating hyphens.',
    );
  }
  const method = options.method ?? 'execute';
  if (
    !/^[a-z][A-Za-z0-9]*$/.test(method) ||
    forbiddenMethods.has(method) ||
    method.length > 80
  ) {
    throw new Error(
      'method must be a non-reserved camelCase Java method name.',
    );
  }
  if (options.write !== undefined && typeof options.write !== 'boolean') {
    throw new Error('write must be a boolean.');
  }
  const interfaceName = options.interfaceName;
  if (
    interfaceName !== undefined &&
    (!/^[A-Z][A-Za-z0-9]*$/.test(interfaceName) || interfaceName.length > 80)
  ) {
    throw new Error(
      'interfaceName must be a PascalCase Java identifier of at most 80 characters.',
    );
  }
  if (
    interfaceName &&
    [
      'Service',
      'Transactional',
      'Override',
      'UnsupportedOperationException',
      'Object',
    ].includes(interfaceName)
  ) {
    throw new Error(
      'interfaceName conflicts with a type used by the service template.',
    );
  }

  const api = readProjectConfiguration(tree, 'api');
  const moduleRoot = joinPathFragments(
    api.root,
    'src/main/java/com/chanakanlabs/theparlor',
    options.module,
  );
  const packageName = `com.chanakanlabs.theparlor.${options.module}`;
  const moduleDeclaration = tree
    .read(joinPathFragments(moduleRoot, 'package-info.java'))
    ?.toString();
  if (
    ['contracts', 'shared'].includes(options.module) ||
    !moduleDeclaration?.includes(`package ${packageName};`) ||
    !/@(?:org\.springframework\.modulith\.)?ApplicationModule\b/.test(
      moduleDeclaration,
    )
  ) {
    throw new Error(
      'Choose an existing business module with an ApplicationModule declaration; contracts and shared are not service destinations.',
    );
  }

  const className = names(options.name).className;
  if (className === 'Service')
    throw new Error('name must identify a feature, not just Service.');
  const serviceName = className.endsWith('Service')
    ? className
    : `${className}Service`;
  if (interfaceName === serviceName) {
    throw new Error('interfaceName must differ from the service class name.');
  }
  const applicationRoot = joinPathFragments(moduleRoot, 'internal/application');
  const servicePath = joinPathFragments(applicationRoot, `${serviceName}.java`);
  const outputs = [servicePath];
  if (interfaceName)
    outputs.push(joinPathFragments(moduleRoot, `${interfaceName}.java`));

  // Check the complete output set first so a collision never stages a partial scaffold.
  for (const output of outputs) {
    if (tree.exists(output))
      throw new Error(`Refusing to overwrite existing file: ${output}`);
  }
  const substitutions = {
    packageName,
    serviceName,
    interfaceName,
    method,
    write: options.write ?? false,
    todoTag: `${options.module}-${names(options.name).fileName}`,
    tmpl: '',
  };
  generateFiles(
    tree,
    join(import.meta.dirname, 'files/service'),
    applicationRoot,
    substitutions,
  );
  if (interfaceName) {
    generateFiles(
      tree,
      join(import.meta.dirname, 'files/interface'),
      moduleRoot,
      substitutions,
    );
  }
  // Keep writes in the Nx Tree: dry runs remain side-effect free. Do not edit generated contracts,
  // migrations, module dependency declarations or controller wiring. Java formatting uses Spotless.
}

export default backendServiceGenerator;
