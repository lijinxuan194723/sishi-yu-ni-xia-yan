import type {ModelConfig} from './model.ts';

export function validModel(value: unknown): value is ModelConfig {
 if (!value || typeof value !== 'object') return false;
 const model = value as ModelConfig;
 return [model.baseUrl, model.model, model.key].every(value => typeof value === 'string' && !!value.trim());
}

// Project only connection fields. Never bundle shared credentials or silently
// attach another provider to a user-saved connection.
function copyModel(model: ModelConfig): ModelConfig {
 const result: ModelConfig = {baseUrl: model.baseUrl, model: model.model, key: model.key};
 if (validModel(model.fallback)) {
  result.fallback = {baseUrl: model.fallback.baseUrl, model: model.fallback.model, key: model.fallback.key};
 }
 return result;
}

export function defaultModel(): ModelConfig | undefined {
 try {
  const personal = JSON.parse(window.LukeAndroid?.defaultModel?.() || 'null');
  return validModel(personal) ? copyModel(personal) : undefined;
 } catch { return undefined; }
}

export function resolveModel(stored: unknown, builtIn: ModelConfig | undefined): ModelConfig {
 if (validModel(stored)) return copyModel(stored);
 return validModel(builtIn) ? copyModel(builtIn) : {baseUrl: '', model: '', key: ''};
}
