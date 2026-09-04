<script setup lang="ts">
import { computed, reactive } from 'vue';
import { useI18n } from 'vue-i18n';
import OwnerLayout from '../../layouts/OwnerLayout.vue';
import PageBreadcrumb from '../../components/PageBreadcrumb.vue';
import UiButton from '../../components/ui/UiButton.vue';
import UiCard from '../../components/ui/UiCard.vue';
import UiField from '../../components/ui/UiField.vue';
import UiSelect from '../../components/ui/UiSelect.vue';
import UiTextInput from '../../components/ui/UiTextInput.vue';
import {
  descriptionMaxLength,
  categoryOptions,
  type GameFormValues,
} from './form';

/**
 * Shared body for the Add game and Edit game screens. The two frames differ
 * only in their action labels, page title, prefilled values and the cover
 * thumbnail strip, so those are props rather than a duplicated template.
 *
 * The view owns the typing state; the page above it owns the request.
 */
const props = defineProps<{
  breadcrumb: string;
  pageTitle: string;
  secondaryLabel: string;
  primaryLabel: string;
  values: GameFormValues;
  /** Field path (as the API names it) to message key. */
  errors: Record<string, string>;
  pending?: boolean;
  failed?: boolean;
  showThumbnails?: boolean;
}>();

const emit = defineEmits<{ submit: [values: GameFormValues] }>();

const { t } = useI18n();

/**
 * A local copy, seeded once. Typing never reaches the query cache, and a
 * background refetch never reaches the form: the pages above only render this
 * view once the game and the branch directory have loaded, so `props.values` is
 * already complete here, and re-seeding on every new object identity would
 * discard whatever the user had typed. A different game means a different route,
 * which remounts the view (see AppRoot.vue).
 */
const form = reactive<GameFormValues>(clone(props.values));

const lastBranchIndex = computed(() => form.copies.length - 1);

const errorSummary = computed(() => {
  if (Object.keys(props.errors).length > 0) {
    return t('games.form.fixFields');
  }

  return props.failed ? t('games.form.saveFailed') : undefined;
});

function messageFor(field: string): string | undefined {
  const key = props.errors[field];

  return key ? t('games.form.errors.' + key) : undefined;
}

function clone(values: GameFormValues): GameFormValues {
  return {
    ...values,
    tags: [...values.tags],
    copies: values.copies.map((row) => ({ ...row })),
  };
}

const categories = categoryOptions(t);

const submitLabel = computed(() =>
  props.pending ? t('games.form.saving') : props.primaryLabel,
);
</script>

<template>
  <OwnerLayout>
    <div
      class="flex w-full shrink-0 items-center gap-3 border-b border-line bg-surface px-8 py-3.5"
    >
      <PageBreadcrumb :current="breadcrumb" />
      <div class="h-px min-w-0 flex-1" />
      <UiButton variant="outline" to="/games" class="w-[120px]">
        {{ secondaryLabel }}
      </UiButton>
      <UiButton
        type="submit"
        form="game-form"
        class="w-[120px]"
        :disabled="pending"
      >
        {{ submitLabel }}
      </UiButton>
    </div>

    <form
      id="game-form"
      class="flex w-full shrink-0 flex-col items-start gap-6 px-8 pt-6 pb-8"
      @submit.prevent="emit('submit', form)"
    >
      <h1 class="w-full text-[28px] font-semibold text-ink">
        {{ pageTitle }}
      </h1>

      <p
        v-if="errorSummary"
        data-testid="form-error"
        role="alert"
        class="w-full text-[13px] leading-[20px] text-danger-fg"
      >
        {{ errorSummary }}
      </p>

      <div class="flex w-full flex-col items-start gap-5">
        <!--
          The catalogue publishes each title and description in both languages
          the store serves. English is the entry every game carries; Thai is the
          translation a Thai reader sees, and a game without one falls back to
          English rather than showing nothing.
        -->
        <UiCard :title="t('games.form.gameInformation')">
          <div class="flex w-full items-start gap-4">
            <UiField
              :label="t('games.form.gameTitleEn')"
              input-id="game-title-en"
              :error="messageFor('title.en')"
            >
              <UiTextInput
                id="game-title-en"
                v-model="form.titleEn"
                class="w-full"
                :invalid="Boolean(messageFor('title.en'))"
                :placeholder="t('games.form.gameTitlePlaceholder')"
              />
            </UiField>
            <UiField
              :label="t('games.form.gameTitleTh')"
              input-id="game-title-th"
              :error="messageFor('title.th')"
            >
              <UiTextInput
                id="game-title-th"
                v-model="form.titleTh"
                class="w-full"
                lang="th"
                :invalid="Boolean(messageFor('title.th'))"
                :placeholder="t('games.form.gameTitleThPlaceholder')"
              />
            </UiField>
          </div>

          <p class="w-full text-[12px] leading-[18px] text-ink-muted">
            {{ t('games.form.fallbackHint') }}
          </p>

          <div class="flex w-full items-start gap-4">
            <UiField
              :label="t('games.form.shortDescriptionEn')"
              input-id="game-description-en"
              :error="messageFor('description.en')"
            >
              <textarea
                id="game-description-en"
                v-model="form.descriptionEn"
                :placeholder="t('games.form.descriptionPlaceholder')"
                :maxlength="descriptionMaxLength"
                class="h-[92px] w-full resize-none rounded-md border border-line bg-surface px-3.5 py-3 text-[13px] leading-[20px] text-ink outline-none placeholder:text-ink-muted focus:border-primary"
              />
              <p
                class="w-full text-[12px] leading-[18px] whitespace-pre-wrap text-ink-muted"
              >
                {{
                  t('games.form.descriptionHint', {
                    count: form.descriptionEn.length,
                    max: descriptionMaxLength,
                  })
                }}
              </p>
            </UiField>
            <UiField
              :label="t('games.form.shortDescriptionTh')"
              input-id="game-description-th"
              :error="messageFor('description.th')"
            >
              <textarea
                id="game-description-th"
                v-model="form.descriptionTh"
                lang="th"
                :placeholder="t('games.form.descriptionThPlaceholder')"
                :maxlength="descriptionMaxLength"
                class="h-[92px] w-full resize-none rounded-md border border-line bg-surface px-3.5 py-3 text-[13px] leading-[20px] text-ink outline-none placeholder:text-ink-muted focus:border-primary"
              />
              <p
                class="w-full text-[12px] leading-[18px] whitespace-pre-wrap text-ink-muted"
              >
                {{
                  t('games.form.descriptionHint', {
                    count: form.descriptionTh.length,
                    max: descriptionMaxLength,
                  })
                }}
              </p>
            </UiField>
          </div>
        </UiCard>

        <!--
          The contract carries no cover image, so this frame stays a visual
          placeholder until media is designed into the API.
        -->
        <UiCard :title="t('games.form.media')">
          <div
            class="flex w-full flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-line-strong bg-surface-sunken px-5 py-8"
          >
            <p class="text-[14px] leading-[21px] font-semibold text-ink">
              {{ t('games.form.dropzoneTitle') }}
            </p>
            <p class="text-[12px] leading-[18px] whitespace-pre text-ink-muted">
              {{ t('games.form.dropzoneHint') }}
            </p>
            <UiButton
              variant="outline"
              size="sm"
              disabled
              class="w-[120px] bg-surface text-ink"
            >
              {{ t('games.form.browseFiles') }}
            </UiButton>
          </div>

          <div v-if="showThumbnails" class="flex w-full items-start gap-3">
            <div
              v-for="index in 4"
              :key="index"
              class="h-[76px] min-w-0 flex-1 rounded-table border bg-surface-sunken"
              :class="index === 1 ? 'border-primary' : 'border-line'"
            />
          </div>
        </UiCard>

        <UiCard :title="t('games.form.gameplay')">
          <div class="flex w-full items-start gap-4">
            <UiField
              :label="t('games.form.category')"
              input-id="game-category"
              :error="messageFor('category')"
            >
              <UiSelect
                id="game-category"
                v-model="form.category"
                class="w-full"
                :invalid="Boolean(messageFor('category'))"
                :placeholder="t('games.form.categoryPlaceholder')"
                :options="categories"
              />
            </UiField>
            <!--
              The design draws a select here, but ships no option list and the
              API stores a plain number of minutes, so this is a number field.
            -->
            <UiField
              :label="t('games.form.playTime')"
              input-id="game-play-time"
              :error="messageFor('playTimeMinutes')"
            >
              <UiTextInput
                id="game-play-time"
                v-model="form.playTimeMinutes"
                class="w-full"
                inputmode="numeric"
                :invalid="Boolean(messageFor('playTimeMinutes'))"
                :placeholder="t('games.form.playTimePlaceholder')"
              />
            </UiField>
          </div>

          <div class="flex w-full items-start gap-4">
            <UiField
              :label="t('games.form.minPlayers')"
              input-id="game-min"
              :error="messageFor('minPlayers')"
            >
              <UiTextInput
                id="game-min"
                v-model="form.minPlayers"
                class="w-full"
                inputmode="numeric"
                :invalid="Boolean(messageFor('minPlayers'))"
                :placeholder="t('games.form.minPlayersPlaceholder')"
              />
            </UiField>
            <UiField
              :label="t('games.form.maxPlayers')"
              input-id="game-max"
              :error="messageFor('maxPlayers')"
            >
              <UiTextInput
                id="game-max"
                v-model="form.maxPlayers"
                class="w-full"
                inputmode="numeric"
                :invalid="Boolean(messageFor('maxPlayers'))"
                :placeholder="t('games.form.maxPlayersPlaceholder')"
              />
            </UiField>
            <UiField
              :label="t('games.form.difficulty')"
              input-id="game-difficulty"
              :error="messageFor('difficulty')"
            >
              <UiTextInput
                id="game-difficulty"
                v-model="form.difficulty"
                class="w-full"
                :invalid="Boolean(messageFor('difficulty'))"
                :placeholder="t('games.form.difficultyPlaceholder')"
              />
            </UiField>
          </div>
        </UiCard>

        <UiCard :title="t('games.form.copiesPerBranch')">
          <div
            class="w-full overflow-hidden rounded-table border border-line bg-surface"
          >
            <table class="w-full table-fixed border-collapse text-left">
              <colgroup>
                <col />
                <col class="w-[182px]" />
              </colgroup>
              <thead>
                <tr
                  class="border-b border-line bg-surface-sunken text-[12px] leading-[18px] font-semibold text-ink-secondary"
                >
                  <th scope="col" class="py-2.5 pr-1.5 pl-4 font-semibold">
                    {{ t('games.form.branch') }}
                  </th>
                  <th scope="col" class="py-2.5 pr-4 pl-1.5 font-semibold">
                    {{ t('games.form.copies') }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, index) in form.copies"
                  :key="row.branchId"
                  :class="index < lastBranchIndex ? 'border-b border-line' : ''"
                >
                  <th
                    scope="row"
                    class="py-2.5 pr-1.5 pl-4 text-left text-[13px] leading-[20px] font-semibold text-ink"
                  >
                    <label :for="'copies-' + index">{{ row.branchName }}</label>
                  </th>
                  <td class="py-2.5 pr-4 pl-1.5">
                    <UiTextInput
                      :id="'copies-' + index"
                      v-model="row.copies"
                      class="w-full"
                      inputmode="numeric"
                      :invalid="
                        Boolean(messageFor('copies[' + index + '].copies'))
                      "
                    />
                    <p
                      v-if="messageFor('copies[' + index + '].copies')"
                      class="pt-1 text-[12px] leading-[18px] text-danger-fg"
                    >
                      {{ messageFor('copies[' + index + '].copies') }}
                    </p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p
            v-if="messageFor('copies')"
            class="text-[12px] leading-[18px] text-danger-fg"
          >
            {{ messageFor('copies') }}
          </p>
        </UiCard>
      </div>
    </form>
  </OwnerLayout>
</template>
