// eslint-disable-next-line ember/no-classic-components
import Component from '@ember/component';
import { action, computed } from '@ember/object';
import { reads } from '@ember/object/computed';
import { inject } from '@ember/service';
import { tagName } from '@ember-decorators/component';
// eslint-disable-next-line ember/no-mixins
import Save from 'api-umbrella-admin-ui/mixins/save';
import classic from 'ember-classic-decorator';
import escape from 'lodash-es/escape';

@classic
@tagName("")
export default class RecordForm extends Component.extend(Save) {
  @inject()
  session;

  @reads('session.data.authenticated.admin')
  currentAdmin;

  @computed('currentAdmin.permissions.admin_manage')
  get isDisabled() {
    return !this.currentAdmin.permissions.admin_manage;
  }

  @action
  submitForm(event) {
    event.preventDefault();
    this.saveRecord({
      element: event.target,
      transitionToRoute: 'admin_groups',
      message: 'Successfully saved the admin group "' + escape(this.model.name) + '"',
    });
  }

  @action
  delete() {
    this.destroyRecord({
      prompt: 'Are you sure you want to delete the admin group "' + escape(this.model.name) + '"?',
      transitionToRoute: 'admin_groups',
      message: 'Successfully deleted the admin group "' + escape(this.model.name) + '"',
    });
  }
}
