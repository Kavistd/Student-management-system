
export interface Student {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  course: string | null;
}

/** Request body for POST/PUT /api/students (StudentRequest). */
export interface StudentPayload {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  course: string | null;
}
