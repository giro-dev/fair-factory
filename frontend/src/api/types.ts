import type { components } from './schema'

// The OpenAPI spec does not mark server-owned fields as required/nullable (Java records
// have no required list and JPA columns can be null), so the generated types are looser
// than reality. These aliases tighten `id` (always present) and declare the fields the
// backend may return as null.
export type AutomationRule = components['schemas']['AutomationRuleDto'] & {
  id: string
  lastRunAt?: string | null
}
export type RuleType = NonNullable<AutomationRule['type']>
export type CalendarEvent = components['schemas']['CalendarEventDto'] & {
  id: string
  time?: string | null
  durationMinutes?: number | null
  source?: 'LOCAL' | 'ICS'
  subscriptionId?: string | null
}
export type CalendarSubscription = components['schemas']['CalendarSubscriptionDto'] & {
  id: string
  color?: string | null
  lastSyncedAt?: string | null
  lastError?: string | null
}
export type Chore = components['schemas']['ChoreDto'] & { id: string }
export type Household = components['schemas']['HouseholdDto'] & { id: string }
export type Member = components['schemas']['MemberDto'] & { id: string }
export type MealPlan = components['schemas']['MealPlanDto']
export type MealSlot = components['schemas']['MealSlotDto']
export type Recipe = components['schemas']['RecipeDto'] & { id: string }
export type Ingredient = NonNullable<Recipe['ingredients']>[number]
export type RecipePage = Omit<components['schemas']['RecipePageDto'], 'content'> & {
  content?: Recipe[]
}
export type ImportCandidate = components['schemas']['CandidateInfo'] & { key: string }
export type RecipeImportSession = Omit<
  components['schemas']['ImportSessionInfo'],
  'id' | 'candidates'
> & {
  id: string
  candidates?: ImportCandidate[]
}
export type RecipeImportOutcome = components['schemas']['ImportOutcome']

// Hand-written mirror of the criteria-filter request contract (the generated schema for the
// deductive FilterNode union is looser than the JSON the backend accepts).
export type FilterCondition = { field: string; operator: string; value?: unknown; values?: unknown[] }
export type FilterNode =
  | FilterCondition
  | { and?: FilterNode[]; or?: FilterNode[]; combinator?: string; filters?: FilterNode[] }
export type RecipeSearchBody = { filter: FilterNode }
export type ShoppingList = components['schemas']['ShoppingListDto'] & { id: string }
export type ShoppingItem = components['schemas']['ShoppingItemDto'] & { id: string }
export type AgentDefinition = components['schemas']['AgentDefinition']
export type AgentConfig = components['schemas']['AgentConfigDto'] & {
  id: string
  provider?: string | null
  model?: string | null
  temperature?: number | null
  maxTokens?: number | null
}
export type ProviderInfo = components['schemas']['ProviderDto'] & { error?: string | null }
export type AgentTestResult = components['schemas']['AgentTestResult']
export type ImportResult = components['schemas']['ImportResult']
