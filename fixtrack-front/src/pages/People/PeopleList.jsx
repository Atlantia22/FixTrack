import { useState } from 'react'
import { MdAdd, MdDeleteOutline, MdOutlineEdit } from 'react-icons/md'
import ConfirmDialog from '../../components/ConfirmDialog/ConfirmDialog'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import { useToast } from '../../hooks/useToast'
import * as personService from '../../services/personService'
import { getErrorMessage } from '../../utils/errors'
import { displayOrNT } from '../../utils/format'
import { AVAILABILITY_LABELS, ROLE_LABELS, ROLES, toOptions } from '../../utils/labels'
import PersonFormModal from '../../components/PersonFormModal/PersonFormModal'
import './PeopleList.css'

const STAFF_ROLES = toOptions(ROLE_LABELS).filter(({ value }) => value !== ROLES.CUSTOMER)

// Listado del personal interno con filtros, alta, edición y baja; los clientes están en Clientes y equipos
export default function PeopleList() {
  const { showError, showSuccess } = useToast()
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [active, setActive] = useState(true)
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState(null)
  const [toDeactivate, setToDeactivate] = useState(null)
  const debouncedSearch = useDebouncedValue(search)

  const people = useRemoteData(
    () => personService.searchPeople({ search: debouncedSearch, role, staff: true, active, page }),
    [debouncedSearch, role, active, page],
  )
  const specialties = useRemoteData(personService.listSpecialties, [])
  useRealtime(TOPICS.PERSONS, ({ payload }) => {
    if (payload.role !== ROLES.CUSTOMER) people.reload({ silent: true })
  })

  function changeFilter(setter) {
    return (value) => {
      setter(value)
      setPage(0)
    }
  }

  async function handleDeactivate() {
    try {
      await personService.deactivatePerson(toDeactivate.id)
      showSuccess(`Se dio de baja a ${toDeactivate.fullName}.`)
      setToDeactivate(null)
      people.reload()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
      setToDeactivate(null)
    }
  }

  const columns = [
    { key: 'document', header: 'Documento', render: (person) => `${person.documentType} ${person.documentNumber ?? '—'}` },
    { key: 'fullName', header: 'Nombre' },
    { key: 'role', header: 'Rol', render: (person) => <span className="tag">{ROLE_LABELS[person.role]}</span> },
    { key: 'phone', header: 'Celular', render: (person) => displayOrNT(person.phone) },
    { key: 'email', header: 'Correo', render: (person) => displayOrNT(person.email) },
    {
      key: 'detail',
      header: 'Especialidades',
      render: (person) =>
        person.role === ROLES.TECHNICIAN ? (
          <span className="people-page__technician">
            {person.specialties.map((specialty) => specialty.name).join(', ') || 'Sin especialidades'}
            {person.availability && <span className="tag">{AVAILABILITY_LABELS[person.availability]}</span>}
          </span>
        ) : '—',
    },
    {
      key: 'actions',
      header: 'Acciones',
      align: 'right',
      render: (person) =>
        person.active ? (
          <span className="row-actions">
            <button type="button" className="action-btn action-btn--edit" aria-label={`Editar a ${person.fullName}`} onClick={() => setEditing(person)}>
              <MdOutlineEdit aria-hidden="true" />
            </button>
            <button type="button" className="action-btn action-btn--delete" aria-label={`Dar de baja a ${person.fullName}`} onClick={() => setToDeactivate(person)}>
              <MdDeleteOutline aria-hidden="true" />
            </button>
          </span>
        ) : <span className="tag">Dado de baja</span>,
    },
  ]

  return (
    <>
      <div className="people-page__toolbar">
        <div className="filters">
          <FormField id="people-search" label="Buscar">
            {(props) => (
              <input {...props} type="search" className="form-input" placeholder="Nombre, documento, correo o celular" value={search} onChange={(event) => changeFilter(setSearch)(event.target.value)} />
            )}
          </FormField>
          <FormField id="people-role" label="Rol">
            {(props) => (
              <select {...props} className="form-input" value={role} onChange={(event) => changeFilter(setRole)(event.target.value)}>
                <option value="">Todos</option>
                {STAFF_ROLES.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
              </select>
            )}
          </FormField>
          <FormField id="people-active" label="Estado">
            {(props) => (
              <select {...props} className="form-input" value={String(active)} onChange={(event) => changeFilter(setActive)(event.target.value === 'true')}>
                <option value="true">Activas</option>
                <option value="false">Dadas de baja</option>
              </select>
            )}
          </FormField>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setEditing({})}>
          <MdAdd aria-hidden="true" />
          Nuevo usuario
        </button>
      </div>

      <DataTable
        caption="Personal"
        columns={columns}
        rows={people.data?.content ?? []}
        loading={people.loading}
        error={people.error}
        onRetry={people.reload}
        emptyMessage="No hay usuarios con esos filtros."
        page={people.data?.page ?? 0}
        totalPages={people.data?.totalPages ?? 1}
        totalElements={people.data?.totalElements}
        onPageChange={setPage}
      />

      {editing && (
        <PersonFormModal
          key={editing.id ?? 'new'}
          person={editing.id ? editing : null}
          specialties={specialties.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            people.reload()
          }}
        />
      )}

      <ConfirmDialog
        open={toDeactivate !== null}
        title="Dar de baja"
        message={`${toDeactivate?.fullName} saldrá de los listados y no podrá iniciar sesión. Su historial se conserva.`}
        confirmLabel="Dar de baja"
        onConfirm={handleDeactivate}
        onCancel={() => setToDeactivate(null)}
      />
    </>
  )
}
