
export interface Student {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  course: string | null;
  linkedAccount: boolean;
}

/** Request body for PUT /api/students/me (UpdateProfileRequest): the fields a student may edit. */
export interface ProfileUpdatePayload {
  firstName: string;
  lastName: string;
  phone: string;
}

/** Request body for PUT /api/students/{id} (StudentRequest). */
export interface StudentPayload {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  course: string | null;
}
