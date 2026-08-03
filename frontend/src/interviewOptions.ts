export const INTERVIEW_TYPES = [
  'Technical',
  'HR',
  'Aptitude',
  'Coding',
  'System Design',
] as const

export const DIFFICULTIES = ['Easy', 'Medium', 'Hard', 'Expert'] as const

export const DOMAINS_BY_TYPE: Record<string, string[]> = {
  Technical: [
    'Java',
    'Python',
    'JavaScript',
    'TypeScript',
    'SQL',
    'React',
    'Node.js',
    'Spring Boot',
    'C++',
    'Go',
    'DevOps',
    'Cloud AWS',
    'Machine Learning',
    'Android',
  ],
  HR: [
    'Behavioral',
    'Leadership',
    'Culture Fit',
    'Situational',
    'Career Goals',
    'Teamwork',
    'Conflict Resolution',
    'Communication',
  ],
  Aptitude: [
    'Quantitative',
    'Logical Reasoning',
    'Verbal Ability',
    'Data Interpretation',
    'Puzzles',
    'Probability',
    'Number Series',
  ],
  Coding: [
    'Arrays & Strings',
    'Linked Lists',
    'Trees & Graphs',
    'Dynamic Programming',
    'Recursion',
    'Sorting & Searching',
    'Hashing',
    'System Design Lite',
  ],
  'System Design': [
    'Scalability',
    'Databases',
    'Caching',
    'Messaging Queues',
    'API Design',
    'Microservices',
    'Reliability',
    'Security Architecture',
  ],
}

export function domainsForType(type: string): string[] {
  return DOMAINS_BY_TYPE[type] ?? DOMAINS_BY_TYPE.Technical
}

export function allDomains(): string[] {
  return [...new Set(Object.values(DOMAINS_BY_TYPE).flat())]
}
