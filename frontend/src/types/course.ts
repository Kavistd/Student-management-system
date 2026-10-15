
export interface Course {
  id: number;
  courseCode: string;
  courseName: string;
  description: string | null;
  durationMonths: number | null;
}

/** Request body for POST/PUT /api/courses (CourseRequest). */
export interface CoursePayload {
  courseCode: string;
  courseName: string;
  description: string | null;
  durationMonths: number | null;
}
