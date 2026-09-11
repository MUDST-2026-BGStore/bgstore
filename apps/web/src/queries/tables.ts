import { queryOptions } from '@tanstack/vue-query';
import {
  createTable,
  deleteTable,
  listTables,
  updateTable,
} from '../generated/api/sdk.gen';
import type {
  CreateTableRequest,
  ListTablesData,
  TableListResponse,
  UpdateTableRequest,
} from '../generated/api/types.gen';

export type TableListQuery = NonNullable<ListTablesData['query']>;

export const tablesQueryOptions = (query: TableListQuery) =>
  queryOptions({
    queryKey: ['tables', 'list', query] as const,
    queryFn: async (): Promise<TableListResponse> => {
      const { data } = await listTables({ query, throwOnError: true });
      return data;
    },
    placeholderData: (previous) => previous,
  });

export async function createTableRequest(
  body: CreateTableRequest,
): Promise<TableListResponse['items'][number]> {
  const { data } = await createTable({ body, throwOnError: true });
  return data;
}

export async function updateTableRequest(
  tableId: number,
  body: UpdateTableRequest,
): Promise<TableListResponse['items'][number]> {
  const { data } = await updateTable({
    path: { tableId },
    body,
    throwOnError: true,
  });
  return data;
}

export async function deleteTableRequest(tableId: number): Promise<void> {
  await deleteTable({ path: { tableId }, throwOnError: true });
}
