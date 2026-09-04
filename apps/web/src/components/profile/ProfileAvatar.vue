<script setup lang="ts">
defineProps<{
  label: string;
  changeLabel: string;
  editable?: boolean;
  src?: string | null;
}>();

const emit = defineEmits<{
  selected: [file: File];
}>();

const selectImage = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    emit('selected', file);
  }
  input.value = '';
};
</script>

<template>
  <div class="profile-avatar">
    <div class="avatar-visual" role="img" :aria-label="label">
      <img v-if="src" :src="src" alt="" />
      <svg v-else viewBox="0 0 224 224" aria-hidden="true">
        <defs>
          <linearGradient id="avatar-background" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#dbe8e8" />
            <stop offset="1" stop-color="#b5c9cc" />
          </linearGradient>
        </defs>
        <circle cx="112" cy="112" r="112" fill="url(#avatar-background)" />
        <circle cx="112" cy="86" r="42" fill="#f1d4bc" />
        <path
          d="M66 86c0-35 20-57 48-57 30 0 47 23 47 55-10-5-17-13-22-25-15 14-40 23-73 27Z"
          fill="#263b42"
        />
        <path d="M40 224c4-52 32-81 72-81s68 29 72 81H40Z" fill="#497883" />
        <path
          d="M83 148c8 12 18 18 29 18s21-6 29-18l-8-4c-5 8-12 12-21 12s-16-4-21-12l-8 4Z"
          fill="#fff"
          opacity="0.8"
        />
      </svg>
    </div>

    <label v-if="editable" class="avatar-action">
      <span>{{ changeLabel }}</span>
      <input
        class="visually-hidden"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        @change="selectImage"
      />
    </label>
  </div>
</template>

<style scoped>
.profile-avatar {
  position: relative;
  width: 14rem;
  height: 14rem;
}

.avatar-visual {
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 4px solid #fff;
  border-radius: 50%;
  background: #dbe8e8;
  box-shadow: 0 0.75rem 2rem rgb(32 37 45 / 10%);
}

svg,
img {
  display: block;
  width: 100%;
  height: 100%;
}

img {
  object-fit: cover;
}

.avatar-action {
  position: absolute;
  right: 0.35rem;
  bottom: 0.35rem;
  display: grid;
  min-width: 3.25rem;
  min-height: 3.25rem;
  place-items: center;
  padding: 0.5rem 0.7rem;
  border: 3px solid #fff;
  border-radius: 999px;
  color: #fff;
  background: #497883;
  box-shadow: 0 0.4rem 1rem rgb(32 37 45 / 18%);
  font-size: 0.7rem;
  font-weight: 700;
  cursor: pointer;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 720px) {
  .profile-avatar {
    width: 9rem;
    height: 9rem;
  }

  .avatar-visual {
    border-width: 3px;
  }

  .avatar-action {
    min-width: 2.75rem;
    min-height: 2.75rem;
    padding: 0.35rem 0.55rem;
    font-size: 0.62rem;
  }
}
</style>
